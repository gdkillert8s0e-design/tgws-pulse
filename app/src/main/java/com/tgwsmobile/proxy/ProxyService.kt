package com.tgwsmobile.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.net.Socket

/** Whether the local listener actually answers, not merely whether the core reports itself running. */
enum class PortHealth { Unknown, Listening, Unreachable }

class ProxyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    @Volatile private var engine: ProxyEngine? = null
    private var configuration: ProxyConfiguration? = null
    private var monitoring: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var generation = 0
    private var starting = false
        set(value) {
            field = value
            preparing.value = value
        }
    private var stopping = false
        set(value) {
            field = value
            disconnectingState.value = value
        }
    private var pendingStart: ProxyConfiguration? = null
    private var lastCommandId = 0
    private var needsEngineCleanup = true
    private var lastText = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReset: Job? = null

    companion object {
        const val ACTION_START = "com.tgwsmobile.proxy.action.START"
        const val ACTION_STOP = "com.tgwsmobile.proxy.action.STOP"
        const val ACTION_RESTART = "com.tgwsmobile.proxy.action.RESTART"
        const val EXTRA_BIND_IP = "EXTRA_BIND_IP"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_IPS = "EXTRA_IPS"
        const val EXTRA_POOL_SIZE = "EXTRA_POOL_SIZE"
        const val EXTRA_CFPROXY_ENABLED = "EXTRA_CFPROXY_ENABLED"
        const val EXTRA_CF_DISABLE_TLS = "EXTRA_CF_DISABLE_TLS"
        const val EXTRA_CFPROXY_PRIORITY = "EXTRA_CFPROXY_PRIORITY"
        const val EXTRA_CFPROXY_DOMAIN = "EXTRA_CFPROXY_DOMAIN"
        const val EXTRA_CF_WORKER_DOMAIN = "EXTRA_CF_WORKER_DOMAIN"
        const val EXTRA_SECRET_KEY = "EXTRA_SECRET_KEY"

        private const val CHANNEL = "tgws_proxy_status_v6"
        private const val NOTIFICATION = 101

        /** How long the CPU stays awake after the last relayed byte. */
        private const val TRAFFIC_WAKE_MS = 60_000L

        /** Networks flap while switching; act once they settle. */
        private const val NETWORK_SETTLE_MS = 2_000L

        /** Interval of the health check: statistics plus a TCP probe of the local port. */
        private const val MONITOR_INTERVAL_MS = 3_000L
        private const val PROBE_TIMEOUT_MS = 800

        /** Consecutive failed probes before the service stops itself and reports an error. */
        private const val PROBE_FAILURES_BEFORE_STOP = 3

        /** One failed probe is noise; health only drops after this many in a row. */
        private const val PROBE_FAILURES_UNREACHABLE = 2

        private val preparing = MutableStateFlow(false)
        val isStarting: StateFlow<Boolean> = preparing

        private val running = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = running

        private val connection = MutableStateFlow<ActiveProxyConnection?>(null)
        val activeConnection: StateFlow<ActiveProxyConnection?> = connection

        private val disconnectingState = MutableStateFlow(false)
        /** True from the moment a stop starts until its cleanup finishes. */
        val isStopping: StateFlow<Boolean> = disconnectingState

        private val health = MutableStateFlow(PortHealth.Unknown)
        /** True once at least one real session reached Telegram through an upstream route. */
        private val reachedTelegram = MutableStateFlow(false)
        /** True once a session reached Telegram; a listening port alone is not "connected". */
        val telegramConnected: StateFlow<Boolean> = reachedTelegram
        val portHealth: StateFlow<PortHealth> = health

        private val statsState = MutableStateFlow(ProxyStats())
        val stats: StateFlow<ProxyStats> = statsState

        private val startedState = MutableStateFlow(0L)
        /** Wall-clock start time in millis, 0 while stopped. */
        val startedAt: StateFlow<Long> = startedState

        private val errorState = MutableStateFlow<String?>(null)
        /** Last failure shown to the user; cleared by the next start attempt. */
        val lastError: StateFlow<String?> = errorState

        /** True only when the engine reports running AND the local port answers. */
        fun isServing(): Boolean = running.value && health.value == PortHealth.Listening
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastCommandId = startId
        when (intent?.action) {
            ACTION_START -> start(
                ProxyConfiguration(
                    intent.getStringExtra(EXTRA_BIND_IP) ?: "127.0.0.1",
                    intent.getIntExtra(EXTRA_PORT, 1443),
                    intent.getStringExtra(EXTRA_IPS) ?: "",
                    intent.getStringExtra(EXTRA_SECRET_KEY) ?: "",
                    intent.getIntExtra(EXTRA_POOL_SIZE, 4),
                    intent.getBooleanExtra(EXTRA_CFPROXY_ENABLED, true),
                    intent.getStringExtra(EXTRA_CFPROXY_DOMAIN) ?: "",
                    intent.getStringExtra(EXTRA_CF_WORKER_DOMAIN) ?: "",
                    intent.getBooleanExtra(EXTRA_CF_DISABLE_TLS, false),
                )
            )
            ACTION_RESTART -> configuration?.let { restart(it) } ?: stopSelfResult(startId)
            ACTION_STOP -> stop()
            else -> stopSelfResult(startId)
        }
        return START_REDELIVER_INTENT
    }

    private fun foreground(text: String) {
        lastText = text
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION, notification(text))
        }
    }

    private fun start(config: ProxyConfiguration) {
        if (stopping) {
            // The UI can request another start as soon as running becomes false,
            // while the previous listener is still closing.
            pendingStart = config
            Log.i("TgWsProxy", "Proxy start queued until cleanup completes")
            return
        }
        if (starting || running.value) return
        configuration = config
        needsEngineCleanup = true
        errorState.value = null
        starting = true
        val current = ++generation
        foreground(getString(R.string.notification_starting))
        holdAwake(TRAFFIC_WAKE_MS)
        scope.launch {
            operations.withLock {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val runtime = engine ?: PythonProxyEngine(this@ProxyService).also { engine = it }
                        // Engine initialization can outlive a stop request. Do not open
                        // a listener after that request.
                        if (generation != current) runtime.stop() else runtime.start(config)
                    }
                    if (generation != current) return@withLock
                    starting = false
                    if (result.connection == null) {
                        val reason = result.error ?: result.state
                        Log.e("TgWsProxy", "Proxy start failed: $reason")
                        journal("error", "start failed: $reason")
                        errorState.value = getString(R.string.error_start_failed, reason)
                        update(getString(R.string.notification_error, reason))
                        stop()
                    } else {
                        connection.value = result.connection
                        running.value = true
                        startedState.value = System.currentTimeMillis()
                        // Verify the port before the UI can show "connected".
                        val answers = withContext(Dispatchers.IO) { probePort(config.host, config.port) }
                        health.value = if (answers) PortHealth.Listening else PortHealth.Unreachable
                        update(getString(R.string.notification_running))
                        journal(
                            "info",
                            "started on ${config.host}:${config.port}, pool ${config.poolSize}, " +
                                "cf ${config.cfEnabled}, cf_tls ${!config.disableSecure}",
                        )
                        watchNetwork()
                        monitoring = scope.launch { monitor() }
                    }
                } catch (e: Exception) {
                    if (generation == current) {
                        Log.e("TgWsProxy", "Proxy startup failed", e)
                        starting = false
                        errorState.value = getString(R.string.error_start_failed, e.message ?: "unknown")
                        stop()
                    }
                }
            }
        }
    }

    /**
     * Periodic check: the engine's own state, the traffic counters and a real TCP
     * probe of the local port. Two consecutive failures mark the port unreachable;
     * [PROBE_FAILURES_BEFORE_STOP] failures stop the service with an error. The CPU
     * is kept awake only while bytes move, so an idle proxy lets the phone sleep.
     */
    private suspend fun monitor() {
        var lastBytes = -1L
        var failures = 0
        while (currentCoroutineContext().isActive) {
            delay(MONITOR_INTERVAL_MS)
            val config = configuration ?: break
            val result = try {
                withContext(Dispatchers.IO) { engine?.snapshot() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TgWsProxy", "Proxy status check failed", e)
                journal("error", "status check failed: $e")
                errorState.value = getString(R.string.error_engine_stopped)
                stop()
                break
            } ?: break

            if (result.connection == null) {
                journal("error", "engine stopped by itself: ${result.error ?: result.state}")
                errorState.value = getString(R.string.error_engine_stopped)
                stop()
                break
            }
            statsState.value = result.stats

            // A listening local port only means the bridge is up. "Connected" is
            // a real session that reached Telegram over any upstream route.
            val reached = result.stats.viaWs > 0 || result.stats.viaCloudflare > 0 ||
                result.stats.viaDirect > 0 || result.stats.viaFronting > 0
            reachedTelegram.value = reached

            val answers = withContext(Dispatchers.IO) { probePort(config.host, config.port) }
            if (answers) {
                if (failures > 0) journal("info", "local port answers again")
                failures = 0
                health.value = PortHealth.Listening
            } else {
                failures++
                if (failures == 1) journal("warn", "local port ${config.port} does not answer")
                if (failures >= PROBE_FAILURES_UNREACHABLE) health.value = PortHealth.Unreachable
                if (failures >= PROBE_FAILURES_BEFORE_STOP) {
                    journal("error", "local port stopped answering; stopping")
                    errorState.value = getString(R.string.error_port_unreachable)
                    stop()
                    break
                }
            }

            val bytes = result.stats.bytesUp + result.stats.bytesDown
            if (bytes != lastBytes) {
                holdAwake(TRAFFIC_WAKE_MS)
                lastBytes = bytes
            }
            val human = if (bytes < 1_048_576) "${bytes / 1024} KB" else "${bytes / 1_048_576} MB"
            update(getString(R.string.notification_traffic, human, result.stats.active))
        }
    }

    /** A TCP connect that is closed at once; the core treats it as a disconnect before handshake. */
    private fun probePort(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ProxyLink.clientHost(host), port), PROBE_TIMEOUT_MS) }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * The core backs off failed routes for up to an hour, and only a restart used to
     * clear that. Reset it when Android switches networks or regains validated
     * internet, so recovery needs no manual restart.
     */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null
            private var validated = false
            private var initialized = false
            private var seenValidation = false

            override fun onAvailable(network: Network) {
                val changed = initialized && network != current
                current = network
                initialized = true
                if (changed) scheduleNetworkReset("switched network")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (network != current) return
                val now = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (now && !validated && initialized && seenValidation) scheduleNetworkReset("internet is back")
                if (now) seenValidation = true
                validated = now
            }

            override fun onLost(network: Network) {
                if (network == current) {
                    current = null
                    validated = false
                }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Log.w("TgWsProxy", "Network callback unavailable", it) }
    }

    private fun scheduleNetworkReset(reason: String) {
        networkReset?.cancel()
        networkReset = scope.launch {
            delay(NETWORK_SETTLE_MS)
            if (!running.value) return@launch
            holdAwake(TRAFFIC_WAKE_MS)
            // Keep the last known health while probing: a network switch must not
            // flip the UI back to 'connecting' and replay the orb animation.
            val reset = withContext(Dispatchers.IO) {
                runCatching {
                    engine?.note("info", "network: $reason")
                    engine?.networkChanged()
                }.getOrNull()
            }
            val answers = withContext(Dispatchers.IO) {
                configuration?.let { probePort(it.host, it.port) } ?: false
            }
            health.value = if (answers) PortHealth.Listening else PortHealth.Unreachable
            Log.i("TgWsProxy", "Network change ($reason): routes reset = $reset, port ok = $answers")
        }
    }

    private fun unwatchNetwork() {
        networkReset?.cancel()
        networkReset = null
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private fun journal(level: String, message: String) {
        val runtime = engine ?: return
        CoroutineScope(Dispatchers.IO).launch { runCatching { runtime.note(level, message) } }
    }

    private fun restart(config: ProxyConfiguration) {
        if (starting || stopping) return
        stop(restart = config)
    }

    private fun stop(restart: ProxyConfiguration? = null) {
        // A later explicit stop also cancels a queued start or restart.
        pendingStart = restart
        if (stopping) return
        val stopCommandId = lastCommandId
        val interruptStartup = starting
        stopping = true
        ++generation
        starting = false
        monitoring?.cancel()
        monitoring = null
        unwatchNetwork()
        if (running.value) journal("info", if (restart != null) "restart requested" else "stopped")
        connection.value = null
        running.value = false
        health.value = PortHealth.Unknown
        reachedTelegram.value = false
        statsState.value = ProxyStats()
        startedState.value = 0L
        update(getString(R.string.notification_stopping))
        scope.launch {
            // Signal Python before waiting for the start operation's mutex, so a
            // start that is still opening the listener is interrupted.
            if (interruptStartup) {
                try {
                    withContext(Dispatchers.IO) { engine?.stop() }
                } catch (e: Exception) {
                    Log.w("TgWsProxy", "Startup cancellation failed", e)
                }
            }
            operations.withLock {
                try {
                    val result = withContext(Dispatchers.IO) { engine?.stop() }
                    needsEngineCleanup = result != null && result.state != "stopped"
                } catch (e: Exception) {
                    Log.e("TgWsProxy", "Proxy cleanup failed", e)
                }
            }
            releaseWakeLock()
            stopping = false
            val next = pendingStart
            pendingStart = null
            if (next != null) {
                start(next)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                // Do not destroy this service if a newer start command has already
                // been accepted by Android but not yet delivered.
                stopSelfResult(stopCommandId)
            }
        }
    }

    private fun update(text: String) {
        if (lastText == text) return
        lastText = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
    }

    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), flags)
        fun action(name: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, ProxyService::class.java).setAction(name), flags
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 3, Intent(this, ProxyNotificationStopReceiver::class.java).setAction(ACTION_STOP), flags
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mark_mono)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_popup_sync, getString(R.string.notification_restart), action(ACTION_RESTART, 2))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_disconnect), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Re-arms one non-counted wake lock; it lapses on its own when traffic stops. */
    private fun holdAwake(millis: Long) {
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGWS:Proxy")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(millis)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        pendingStart = null
        ++generation
        scope.cancel()
        starting = false
        connection.value = null
        running.value = false
        health.value = PortHealth.Unknown
        startedState.value = 0L
        unwatchNetwork()
        releaseWakeLock()
        // Service teardown must also clean up when Android did not send ACTION_STOP.
        if (needsEngineCleanup) CoroutineScope(Dispatchers.IO).launch {
            runCatching { engine?.stop() }
            operations.withLock { runCatching { engine?.stop() } }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
