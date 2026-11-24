package com.tgwsmobile.proxy.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tgwsmobile.proxy.ActiveProxyConnection
import com.tgwsmobile.proxy.ProxyLink

/** Upstream Flowseal core, linked from the About section and the quick actions. */
const val SOURCE_URL = "https://github.com/Flowseal/tg-ws-proxy"

/** Copies the tg://proxy link for the running connection to the clipboard. */
fun copyTgLink(context: Context, connection: ActiveProxyConnection) {
    val link = ProxyLink.uri(connection.host, connection.port, connection.secret)
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("tg://proxy", link))
}

fun openBatterySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        // Some vendors remove this screen; the user can still reach it from system settings.
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing to open.
    }
}
