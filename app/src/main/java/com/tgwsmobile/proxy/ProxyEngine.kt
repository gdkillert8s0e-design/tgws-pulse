package com.tgwsmobile.proxy

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

data class ActiveProxyConnection(val host: String, val port: Int, val secret: String)

data class ProxyConfiguration(
    val host: String,
    val port: Int,
    val dcIps: String,
    val secret: String,
    val poolSize: Int = 4,
    val cfEnabled: Boolean = true,
    val cfDomain: String = "",
    val cfWorkerDomain: String = "",
    val disableSecure: Boolean = false,
) {
    fun toJson(): String = JSONObject().put("host", host).put("port", port)
        .put("dc_ips", dcIps).put("secret", secret).put("pool_size", poolSize)
        .put("cf_enabled", cfEnabled).put("cf_domain", cfDomain)
        .put("cf_worker_domain", cfWorkerDomain).put("disable_secure", disableSecure).toString()
}

/** Counters reported by the Flowseal core, already aggregated for the UI. */
data class ProxyStats(
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val active: Int = 0,
    val viaWs: Int = 0,
    val viaCloudflare: Int = 0,
    val viaDirect: Int = 0,
    val viaFronting: Int = 0,
)

data class EngineSnapshot(
    val state: String,
    val connection: ActiveProxyConnection?,
    val stats: ProxyStats,
    val error: String?,
)

interface ProxyEngine {
    fun start(configuration: ProxyConfiguration): EngineSnapshot
    fun stop(): EngineSnapshot
    fun snapshot(): EngineSnapshot
    /** Drops route backoff and idle pools left from the previous network. */
    fun networkChanged(): Boolean
    /** Writes a service event into the persistent journal. */
    fun note(level: String, message: String)
}

/** All calls are made on a worker dispatcher, never on the Android UI thread. */
class PythonProxyEngine(context: Context) : ProxyEngine {
    init {
        synchronized(PythonProxyEngine::class.java) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        }
        module.callAttr("set_log_file", ProxyJournal.file(context.applicationContext).path)
    }

    private val module get() = Python.getInstance().getModule("android_proxy")

    // The Python adapter serializes mutations while allowing stop() to signal an
    // in-flight start(). A Java monitor here would prevent that cancellation.
    override fun start(configuration: ProxyConfiguration) =
        decode(module.callAttr("start", configuration.toJson()).toString())

    override fun stop() = decode(module.callAttr("stop").toString())

    override fun snapshot() = decode(module.callAttr("snapshot").toString())

    override fun networkChanged() = module.callAttr("network_changed").toBoolean()

    override fun note(level: String, message: String) {
        module.callAttr("note", level, message)
    }

    private fun decode(text: String): EngineSnapshot {
        val data = JSONObject(text)
        val config = data.optJSONObject("config")
        val running = data.optBoolean("running")
        val connection = if (running && config != null) {
            ActiveProxyConnection(
                config.getString("host"),
                config.getInt("port"),
                config.getString("full_secret"),
            )
        } else {
            null
        }
        val raw = data.optJSONObject("stats") ?: JSONObject()
        val stats = ProxyStats(
            bytesUp = raw.optLong("bytes_up"),
            bytesDown = raw.optLong("bytes_down"),
            active = raw.optInt("connections_active"),
            viaWs = raw.optInt("connections_ws"),
            viaCloudflare = raw.optInt("connections_cfproxy"),
            viaDirect = raw.optInt("connections_tcp_fallback"),
            viaFronting = raw.optInt("connections_fronting"),
        )
        return EngineSnapshot(
            state = data.optString("state", "error"),
            connection = connection,
            stats = stats,
            error = data.optString("error").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
