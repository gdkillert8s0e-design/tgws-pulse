package com.tgwsmobile.proxy

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

/** Single entry point for starting and stopping the proxy from any UI or system trigger. */
object ProxyController {
    private val commandLock = Any()
    private var stopGeneration = 0L

    /** Flowseal's default route: direct WebSocket to DC2 and DC4, Cloudflare as fallback. */
    const val AUTO_DC_IPS =
        "2:${SettingsStore.DEFAULT_DIRECT_DC2_IP},4:${SettingsStore.DEFAULT_DIRECT_DC4_IP}"

    /**
     * Starts the foreground service with the saved settings. [showInvalidPortToast]
     * is kept for call-site compatibility; saved settings are always valid because
     * [SettingsStore] clamps them.
     */
    suspend fun startFromSavedSettings(
        context: Context,
        @Suppress("UNUSED_PARAMETER") showInvalidPortToast: Boolean = false,
    ): Boolean {
        val generation = synchronized(commandLock) { stopGeneration }
        val store = SettingsStore(context)
        val settings = store.settings.first()
        val secret = store.ensureSecret()

        synchronized(commandLock) {
            if (generation != stopGeneration) return false
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProxyService::class.java).apply {
                    action = ProxyService.ACTION_START
                    putExtra(ProxyService.EXTRA_BIND_IP, "127.0.0.1")
                    putExtra(ProxyService.EXTRA_PORT, settings.port)
                    putExtra(ProxyService.EXTRA_IPS, AUTO_DC_IPS)
                    putExtra(ProxyService.EXTRA_POOL_SIZE, settings.poolSize)
                    putExtra(ProxyService.EXTRA_CFPROXY_ENABLED, settings.cfEnabled)
                    putExtra(ProxyService.EXTRA_CF_DISABLE_TLS, false)
                    putExtra(ProxyService.EXTRA_CFPROXY_PRIORITY, true)
                    putExtra(
                        ProxyService.EXTRA_CFPROXY_DOMAIN,
                        if (settings.customCfEnabled && settings.cfEnabled) settings.customCfDomain else "",
                    )
                    putExtra(ProxyService.EXTRA_CF_WORKER_DOMAIN, settings.cfWorkerDomain)
                    putExtra(ProxyService.EXTRA_SECRET_KEY, secret)
                }
            )
        }
        return true
    }

    fun stop(context: Context) {
        synchronized(commandLock) {
            stopGeneration++
            context.startService(
                Intent(context, ProxyService::class.java).apply { action = ProxyService.ACTION_STOP }
            )
        }
    }
}
