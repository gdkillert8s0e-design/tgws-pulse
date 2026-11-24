package com.tgwsmobile.proxy

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.security.MessageDigest

/**
 * Hands the proxy to an installed Telegram client through its tg:// handler. Any
 * client that registers the handler works: official, mirrors and forks alike.
 */
object TelegramConnector {

    fun proxyUri(connection: ActiveProxyConnection): Uri =
        Uri.parse(ProxyLink.uri(connection.host, connection.port, connection.secret))

    /** Returns false when no client can take the link, so the UI can say so. */
    fun open(context: Context, connection: ActiveProxyConnection, chooseAgain: Boolean = false): Boolean {
        val base = Intent(Intent.ACTION_VIEW, proxyUri(connection)).addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val clients = pm.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.enabled }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (clients.isEmpty()) {
            Toast.makeText(context, R.string.telegram_not_found, Toast.LENGTH_LONG).show()
            return false
        }

        val preferences = context.getSharedPreferences("telegram_client", Context.MODE_PRIVATE)
        val savedPackage = preferences.getString("package", null)
        val savedSigner = preferences.getString("signer", null)
        val remembered = clients.firstOrNull { it.activityInfo.packageName == savedPackage }
        val currentSigner = remembered?.let { signer(pm, it.activityInfo.packageName) }

        fun launch(packageName: String, signer: String?) {
            try {
                context.startActivity(Intent(base).setPackage(packageName))
                preferences.edit().putString("package", packageName).putString("signer", signer).apply()
            } catch (_: ActivityNotFoundException) {
                preferences.edit().remove("package").remove("signer").apply()
                Toast.makeText(context, R.string.telegram_client_unavailable, Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.telegram_open_failed, Toast.LENGTH_LONG).show()
            }
        }

        // A remembered client is reused only while its signing certificate is unchanged.
        if (!chooseAgain && remembered != null && currentSigner != null && currentSigner == savedSigner) {
            launch(remembered.activityInfo.packageName, currentSigner)
            return true
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.telegram_choose)
            .setItems(clients.map { it.loadLabel(pm) }.toTypedArray()) { _, index ->
                val packageName = clients[index].activityInfo.packageName
                launch(packageName, signer(pm, packageName))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return true
    }

    @Suppress("DEPRECATION")
    private fun signer(pm: PackageManager, packageName: String): String? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        if (signatures.isNullOrEmpty()) return null
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.sorted().joinToString(",")
    }
}
