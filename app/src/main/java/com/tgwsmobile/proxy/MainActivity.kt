package com.tgwsmobile.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgwsmobile.proxy.ui.ConnectionScreen
import com.tgwsmobile.proxy.ui.DiagnosticsScreen
import com.tgwsmobile.proxy.ui.Motion
import com.tgwsmobile.proxy.ui.Palette
import com.tgwsmobile.proxy.ui.ProvideMotionPreferences
import com.tgwsmobile.proxy.ui.QuickAction
import com.tgwsmobile.proxy.ui.RubberDock
import com.tgwsmobile.proxy.ui.SettingsScreen
import com.tgwsmobile.proxy.ui.TgWsTheme
import com.tgwsmobile.proxy.ui.copyTgLink
import com.tgwsmobile.proxy.ui.openBatterySettings
import com.tgwsmobile.proxy.ui.openUrl
import com.tgwsmobile.proxy.ui.SOURCE_URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TgWsTheme {
                ProvideMotionPreferences {
                    AppShell()
                }
            }
        }
    }
}

/** Content sits under the dock. This bottom padding keeps the last item reachable above it. */
private val CONTENT_BOTTOM_PADDING = 104.dp

@Composable
fun AppShell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = ProxySettings())
    SideEffect { Palette.neon = settings.neonStyle }

    val running by ProxyService.isRunning.collectAsStateWithLifecycle()
    val health by ProxyService.portHealth.collectAsStateWithLifecycle()
    val connection by ProxyService.activeConnection.collectAsStateWithLifecycle()
    val serving = running && health == PortHealth.Listening

    val titles = listOf(
        stringResource(R.string.nav_proxy),
        stringResource(R.string.nav_settings),
        stringResource(R.string.nav_diagnostics),
    )
    val labelOpenTelegram = stringResource(R.string.action_open_telegram)
    val labelCopy = stringResource(R.string.action_copy_link)
    val labelBattery = stringResource(R.string.action_battery)
    val labelSource = stringResource(R.string.action_source)
    val labelShare = stringResource(R.string.action_share_journal)

    val actionsFor: (Int) -> List<QuickAction> = { index ->
        when (index) {
            0 -> listOf(
                QuickAction(labelOpenTelegram, serving && connection != null) {
                    connection?.let { TelegramConnector.open(context, it) }
                },
                QuickAction(labelCopy, serving && connection != null) {
                    connection?.let { copyTgLink(context, it) }
                },
            )
            1 -> listOf(
                QuickAction(labelBattery, true) { openBatterySettings(context) },
                QuickAction(labelSource, true) { openUrl(context, SOURCE_URL) },
            )
            else -> listOf(
                QuickAction(labelShare, true) { ProxyJournal.share(context) },
                QuickAction(labelBattery, true) { openBatterySettings(context) },
            )
        }
    }

    // Back closes the expanded card first, before leaving the app.
    BackHandler(enabled = expanded) { expanded = false }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().padding(bottom = CONTENT_BOTTOM_PADDING),
            transitionSpec = {
                // Pages slide in the direction of travel; the outgoing page leaves quicker.
                val forward = targetState > initialState
                (slideInHorizontally(tween(Motion.BASE, easing = Motion.Expressive)) { if (forward) it / 6 else -it / 6 } +
                    fadeIn(tween(Motion.BASE))) togetherWith
                    (slideOutHorizontally(tween(Motion.FAST, easing = Motion.Exit)) { if (forward) -it / 8 else it / 8 } +
                        fadeOut(tween(Motion.FAST)))
            },
            label = "tabs",
        ) { page ->
            when (page) {
                0 -> ConnectionScreen()
                1 -> SettingsScreen()
                else -> DiagnosticsScreen()
            }
        }

        // Tapping outside the open card closes it. Transparent: nothing is dimmed.
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = false },
            )
        }

        RubberDock(
            selected = tab,
            expanded = expanded,
            titles = titles,
            actionsFor = actionsFor,
            onSelect = { tab = it },
            onToggleExpanded = { expanded = !expanded },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
        )
    }
}
