package com.tgwsmobile.proxy.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgwsmobile.proxy.ActiveProxyConnection
import com.tgwsmobile.proxy.ProxyController
import com.tgwsmobile.proxy.ProxyLink
import com.tgwsmobile.proxy.ProxyService
import com.tgwsmobile.proxy.PortHealth
import com.tgwsmobile.proxy.ProxySettings
import com.tgwsmobile.proxy.R
import com.tgwsmobile.proxy.SettingsStore
import com.tgwsmobile.proxy.TelegramConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun accentFor(state: OrbState): Color = when (state) {
    OrbState.Off -> Palette.Idle
    OrbState.Connecting -> Palette.Connecting
    OrbState.Online -> Palette.Online
    OrbState.Error -> Palette.Error
}

/** Main screen: the orb, its status, live numbers and the proxy details. */
@Composable
fun ConnectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SettingsStore(context) }

    val settings by store.settings.collectAsStateWithLifecycle(initialValue = ProxySettings())
    val secret by store.secret.collectAsStateWithLifecycle(initialValue = "")
    val running by ProxyService.isRunning.collectAsStateWithLifecycle()
    val starting by ProxyService.isStarting.collectAsStateWithLifecycle()
    val health by ProxyService.portHealth.collectAsStateWithLifecycle()
    val stats by ProxyService.stats.collectAsStateWithLifecycle()
    val startedAt by ProxyService.startedAt.collectAsStateWithLifecycle()
    val error by ProxyService.lastError.collectAsStateWithLifecycle()
    val connection by ProxyService.activeConnection.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { store.ensureSecret() }

    // "Connected" is shown only when the engine runs AND the local port answers.
    val serving = running && health == PortHealth.Listening
    val orbState = when {
        starting -> OrbState.Connecting
        running && serving -> OrbState.Online
        running -> OrbState.Connecting
        error != null -> OrbState.Error
        else -> OrbState.Off
    }
    val accent by animateColorAsState(
        targetValue = accentFor(orbState),
        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
        label = "screen-accent",
    )

    val launchStart = { scope.launch { ProxyController.startFromSavedSettings(context) } }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { launchStart() }

    fun onOrbTap() {
        if (running || starting) {
            ProxyController.stop(context)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else launchStart()
    }

    // Ticks once per second only while the proxy is running.
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = startedAt) {
        while (startedAt != 0L) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime = if (startedAt == 0L) 0L else ((now - startedAt).coerceAtLeast(0L)) / 1000

    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    val host = connection?.host ?: "127.0.0.1"
    val port = connection?.port ?: settings.port
    val fullSecret = connection?.secret ?: ProxyLink.fullSecret(secret)
    val errorText = error ?: stringResource(R.string.hint_error)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.30f),
                        radius = size.width * 0.95f,
                    )
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Entrance(entered, 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mark_mono),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextMuted,
                        )
                    }
                }
            }

            Entrance(entered, 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PowerOrb(
                        state = orbState,
                        enabled = !starting,
                        onClick = ::onOrbTap,
                    )
                    AnimatedContent(
                        targetState = orbState,
                        transitionSpec = {
                            (slideInVertically(tween(Motion.BASE, easing = Motion.Expressive)) { it / 3 } +
                                fadeIn(tween(Motion.BASE))) togetherWith
                                (slideOutVertically(tween(Motion.FAST, easing = Motion.Exit)) { -it / 3 } +
                                    fadeOut(tween(Motion.FAST)))
                        },
                        label = "status",
                    ) { state ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(
                                    when (state) {
                                        OrbState.Off -> R.string.status_off
                                        OrbState.Connecting -> R.string.status_connecting
                                        OrbState.Online -> R.string.status_online
                                        OrbState.Error -> R.string.status_error
                                    }
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = accentFor(state),
                            )
                            Text(
                                when (state) {
                                    OrbState.Off -> stringResource(R.string.hint_off)
                                    OrbState.Connecting -> stringResource(R.string.hint_connecting)
                                    OrbState.Online -> stringResource(R.string.hint_online)
                                    OrbState.Error -> errorText
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Entrance(entered, 2) {
                SurfaceCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(stringResource(R.string.uptime_title), formatUptime(uptime))
                        Metric(
                            stringResource(R.string.traffic_title),
                            stringResource(R.string.traffic_up_down, formatBytes(stats.bytesUp), formatBytes(stats.bytesDown)),
                            alignEnd = true,
                        )
                    }
                }
            }

            Entrance(entered, 3) {
                SurfaceCard {
                    Text(stringResource(R.string.route_title), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RouteChip(stringResource(R.string.route_ws), stats.viaWs, Modifier.weight(1f))
                        RouteChip(stringResource(R.string.route_cloudflare), stats.viaCloudflare, Modifier.weight(1f))
                        RouteChip(stringResource(R.string.route_direct), stats.viaDirect, Modifier.weight(1f))
                    }
                }
            }

            Entrance(entered, 4) {
                SurfaceCard {
                    Text(stringResource(R.string.details_title), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                    KeyValueRow(stringResource(R.string.label_address), host)
                    KeyValueRow(stringResource(R.string.label_port), port.toString())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { revealSecret = !revealSecret }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.label_secret), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                        AnimatedContent(targetState = revealSecret, label = "secret") { shown ->
                            Text(
                                text = when {
                                    fullSecret.isBlank() -> "—"
                                    shown -> fullSecret
                                    else -> ProxyLink.mask(secret)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                                color = Palette.TextPrimary,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.secret_reveal_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = Palette.TextMuted,
                    )
                }
            }

            Entrance(entered, 5) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PressablePill(
                        text = stringResource(R.string.action_open_telegram),
                        enabled = serving && connection != null,
                        onClick = { connection?.let { TelegramConnector.open(context, it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PressablePill(
                        text = if (copied) stringResource(R.string.action_copied) else stringResource(R.string.action_copy_link),
                        enabled = serving && connection != null,
                        primary = false,
                        onClick = {
                            connection?.let {
                                copyLink(context, ProxyLink.uri(it.host, it.port, it.secret))
                                copied = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun copyLink(context: Context, link: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("tg://proxy", link))
}

/** Staggered entrance: each block fades and rises a little after the previous one. */
@Composable
private fun Entrance(
    visible: MutableTransitionState<Boolean>,
    index: Int,
    content: @Composable () -> Unit,
) {
    val delay = index * Motion.STAGGER
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(Motion.SLOW, delayMillis = delay, easing = Motion.Expressive)) +
            slideInVertically(tween(Motion.SLOW, delayMillis = delay, easing = Motion.Expressive)) { it / 8 },
    ) {
        content()
    }
}

@Composable
private fun Metric(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFont, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            color = Palette.TextPrimary,
        )
    }
}

@Composable
private fun RouteChip(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = Palette.TextMuted)
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically(tween(Motion.FAST)) { it / 2 } + fadeIn(tween(Motion.FAST))) togetherWith
                    (slideOutVertically(tween(Motion.FAST)) { -it / 2 } + fadeOut(tween(Motion.FAST)))
            },
            label = "route-count",
        ) { value ->
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFont, fontSize = 20.sp),
                color = Palette.TextPrimary,
            )
        }
    }
}
