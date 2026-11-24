package com.tgwsmobile.proxy.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

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

/**
 * Spring tokens shared by all interactive motion. Every spring is physical: the
 * dampingRatio sets how much it overshoots, the stiffness sets how fast it moves.
 * Components take these instead of inventing their own numbers.
 */
object Springs {
    /** Finger-down response: quick, a touch of give. */
    val Press: SpringSpec<Float> = spring(dampingRatio = 0.5f, stiffness = 500f)

    /** Icon or label pop on selection. */
    val Pop: SpringSpec<Float> = spring(dampingRatio = 0.45f, stiffness = 420f)

    /** Leading edge of a stretchy highlight: stiff, it reaches the target first. */
    val LeadEdge: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    /** Trailing edge: soft, it lags behind, and the gap between the edges is the stretch. */
    val TrailEdge: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    /** Fusion of two shapes into one: critically damped, so nothing overshoots and bounces back. */
    val Fuse: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    /** Size morphs between two shapes (dock to card). */
    val MorphDp: SpringSpec<Dp> = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
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
