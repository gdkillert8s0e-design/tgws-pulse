package com.tgwsmobile.proxy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    val neonStyle: Boolean = false,
)

/** Appearance and motion customization. Empty [theme] means "migrate from neonStyle". */
data class UiSettings(
    val theme: String = "",
    val launchAnim: String = "Fade",
    val animLevel: String = "Normal",
    val bgEffects: Boolean = true,
    val glow: Float = 0.5f,
    val cardStyle: String = "Soft",
    val buttonStyle: String = "Pill",
    val elementOpacity: Float = 1f,
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
        val NEON = booleanPreferencesKey("neon_style")
        val THEME = stringPreferencesKey("ui_theme")
        val LAUNCH_ANIM = stringPreferencesKey("ui_launch_anim")
        val ANIM_LEVEL = stringPreferencesKey("ui_anim_level")
        val BG_EFFECTS = booleanPreferencesKey("ui_bg_effects")
        val GLOW = floatPreferencesKey("ui_glow")
        val CARD_STYLE = stringPreferencesKey("ui_card_style")
        val BUTTON_STYLE = stringPreferencesKey("ui_button_style")
        val ELEMENT_OPACITY = floatPreferencesKey("ui_element_opacity")
    }

    private fun Preferences.toUiSettings() = UiSettings(
        theme = this[Keys.THEME].orEmpty(),
        launchAnim = this[Keys.LAUNCH_ANIM] ?: "Fade",
        animLevel = this[Keys.ANIM_LEVEL] ?: "Normal",
        bgEffects = this[Keys.BG_EFFECTS] ?: true,
        glow = this[Keys.GLOW] ?: 0.5f,
        cardStyle = this[Keys.CARD_STYLE] ?: "Soft",
        buttonStyle = this[Keys.BUTTON_STYLE] ?: "Pill",
        elementOpacity = this[Keys.ELEMENT_OPACITY] ?: 1f,
    )

    /** Appearance settings; migrates the old neon toggle into a theme name once. */
    val uiSettings: Flow<UiSettings> = context.proxyDataStore.data.map { prefs ->
        val ui = prefs.toUiSettings()
        if (ui.theme.isEmpty()) {
            val migrated = if (prefs[Keys.NEON] == true) "Neon" else "Emerald"
            context.proxyDataStore.edit { it[Keys.THEME] = migrated }
            ui.copy(theme = migrated)
        } else ui
    }

    suspend fun updateUi(transform: (UiSettings) -> UiSettings) {
        context.proxyDataStore.edit { prefs ->
            val next = transform(prefs.toUiSettings())
            prefs[Keys.THEME] = next.theme.ifEmpty { "Emerald" }
            prefs[Keys.LAUNCH_ANIM] = next.launchAnim
            prefs[Keys.ANIM_LEVEL] = next.animLevel
            prefs[Keys.BG_EFFECTS] = next.bgEffects
            prefs[Keys.GLOW] = next.glow.coerceIn(0f, 1f)
            prefs[Keys.CARD_STYLE] = next.cardStyle
            prefs[Keys.BUTTON_STYLE] = next.buttonStyle
            prefs[Keys.ELEMENT_OPACITY] = next.elementOpacity.coerceIn(0.6f, 1f)
        }
    }

    /** Puts every setting back to its default. The secret survives. */
    suspend fun resetAll() {
        context.proxyDataStore.edit { prefs ->
            prefs[Keys.PORT] = DEFAULT_PORT
            prefs[Keys.POOL] = 4
            prefs[Keys.CF] = true
            prefs[Keys.CUSTOM_CF_ENABLED] = false
            prefs[Keys.CUSTOM_CF] = ""
            prefs[Keys.WORKER] = ""
            prefs[Keys.AUTOSTART] = false
            prefs[Keys.THEME] = "Emerald"
            prefs[Keys.LAUNCH_ANIM] = "Fade"
            prefs[Keys.ANIM_LEVEL] = "Normal"
            prefs[Keys.BG_EFFECTS] = true
            prefs[Keys.GLOW] = 0.5f
            prefs[Keys.CARD_STYLE] = "Soft"
            prefs[Keys.BUTTON_STYLE] = "Pill"
            prefs[Keys.ELEMENT_OPACITY] = 1f
        }
    }

    private fun Preferences.toSettings() = ProxySettings(
        port = this[Keys.PORT] ?: DEFAULT_PORT,
        poolSize = this[Keys.POOL] ?: 4,
        cfEnabled = this[Keys.CF] ?: true,
        customCfEnabled = this[Keys.CUSTOM_CF_ENABLED] ?: false,
        customCfDomain = this[Keys.CUSTOM_CF].orEmpty(),
        cfWorkerDomain = this[Keys.WORKER].orEmpty(),
        autostart = this[Keys.AUTOSTART] ?: false,
        neonStyle = this[Keys.NEON] ?: false,
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
            prefs[Keys.NEON] = next.neonStyle
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
