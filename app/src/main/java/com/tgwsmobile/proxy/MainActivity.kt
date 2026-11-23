package com.tgwsmobile.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tgwsmobile.proxy.ui.BottomNav
import com.tgwsmobile.proxy.ui.ConnectionScreen
import com.tgwsmobile.proxy.ui.DiagnosticsScreen
import com.tgwsmobile.proxy.ui.Motion
import com.tgwsmobile.proxy.ui.Palette
import com.tgwsmobile.proxy.ui.ProvideMotionPreferences
import com.tgwsmobile.proxy.ui.SettingsScreen
import com.tgwsmobile.proxy.ui.TgWsTheme

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

@Composable
fun AppShell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Palette.Background,
        bottomBar = { BottomNav(selected = tab, onSelect = { tab = it }) },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding).fillMaxSize(),
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
    }
}
