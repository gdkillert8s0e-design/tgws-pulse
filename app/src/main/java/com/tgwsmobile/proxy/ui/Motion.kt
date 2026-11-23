package com.tgwsmobile.proxy.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion system. Every animation takes its duration and curve from here, so the
 * whole app shares one rhythm:
 *  - FAST: feedback that must feel instant (press, tab indicator, copy confirmation).
 *  - BASE: state changes (status, cards, color of the accent).
 *  - SLOW: entrances and the one-time ring reveal when the proxy comes online.
 */
object Motion {
    const val FAST = 180
    const val BASE = 340
    const val SLOW = 680
    const val STAGGER = 70

    /** Material standard curve for things that move on screen. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Expressive curve with a long, soft landing for entrances. */
    val Expressive: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Exits accelerate and leave quickly. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/** True when the system's animation scale is 0 (Developer options / accessibility "Remove animations"). */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun ProvideMotionPreferences(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
