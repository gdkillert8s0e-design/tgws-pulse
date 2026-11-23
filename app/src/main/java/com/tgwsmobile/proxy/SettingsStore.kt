package com.tgwsmobile.proxy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.proxyDataStore: DataStore<Preferences> by preferencesDataStore(name = "tgws_settings")

data class ProxySettings(
    val port: Int = 1443,
    val poolSize: Int = 4,
    val cfEnabled: Boolean = true,
    val customCfEnabled: Boolean = false,
    val customCfDomain: String = "",
    val cfWorkerDomain: String = "",
    val autostart: Boolean = false,
)

class SettingsStore(private val context: Context) {

    companion object {
        /** Flowseal's default direct route for DC2 and DC4. */
        const val DEFAULT_DIRECT_DC2_IP = "149.154.167.220"
        const val DEFAULT_DIRECT_DC4_IP = "149.154.167.220"
        const val DEFAULT_PORT = 1443
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        const val MIN_POOL = 0
        const val MAX_POOL = 16
    }

    private object Keys {
        val PORT = intPreferencesKey("port")
        val POOL = intPreferencesKey("pool_size")
        val CF = booleanPreferencesKey("cfproxy_enabled")
        val CUSTOM_CF_ENABLED = booleanPreferencesKey("custom_cf_enabled")
        val CUSTOM_CF = stringPreferencesKey("custom_cf_domain")
        val WORKER = stringPreferencesKey("cf_worker_domain")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val SECRET_SEALED = stringPreferencesKey("secret_sealed")
    }

    private fun Preferences.toSettings() = ProxySettings(
        port = this[Keys.PORT] ?: DEFAULT_PORT,
        poolSize = this[Keys.POOL] ?: 4,
        cfEnabled = this[Keys.CF] ?: true,
        customCfEnabled = this[Keys.CUSTOM_CF_ENABLED] ?: false,
        customCfDomain = this[Keys.CUSTOM_CF].orEmpty(),
        cfWorkerDomain = this[Keys.WORKER].orEmpty(),
        autostart = this[Keys.AUTOSTART] ?: false,
    )

    val settings: Flow<ProxySettings> = context.proxyDataStore.data.map { it.toSettings() }

    /** Decrypted secret as a flow; empty until [ensureSecret] has run once. */
    val secret: Flow<String> = context.proxyDataStore.data.map { prefs ->
        prefs[Keys.SECRET_SEALED]?.let { SecretVault.open(it) }
            ?.takeIf { ProxyLink.isValidSecret(it) }
            ?.trim()?.lowercase().orEmpty()
    }

    /** Applies a change, clamping numbers to their valid ranges. */
    suspend fun update(transform: (ProxySettings) -> ProxySettings) {
        context.proxyDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.PORT] = next.port.coerceIn(MIN_PORT, MAX_PORT)
            prefs[Keys.POOL] = next.poolSize.coerceIn(MIN_POOL, MAX_POOL)
            prefs[Keys.CF] = next.cfEnabled
            prefs[Keys.CUSTOM_CF_ENABLED] = next.customCfEnabled
            prefs[Keys.CUSTOM_CF] = next.customCfDomain.trim()
            prefs[Keys.WORKER] = next.cfWorkerDomain.trim()
            prefs[Keys.AUTOSTART] = next.autostart
        }
    }

    /** Returns the stored secret, creating one on first use or when decryption fails. */
    suspend fun ensureSecret(): String {
        val sealed = context.proxyDataStore.data.map { it[Keys.SECRET_SEALED] }.first()
        val existing = sealed?.let { SecretVault.open(it) }
        if (existing != null && ProxyLink.isValidSecret(existing)) return existing.trim().lowercase()
        return regenerateSecret()
    }

    suspend fun regenerateSecret(): String {
        val secret = ProxyLink.newSecret()
        context.proxyDataStore.edit { it[Keys.SECRET_SEALED] = SecretVault.seal(secret) }
        return secret
    }
}
