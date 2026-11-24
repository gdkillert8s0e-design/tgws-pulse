package com.tgwsmobile.proxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos

/** Real connection state, mapped from ProxyService. Nothing here is simulated. */
enum class OrbState { Off, Connecting, Online, Error, Disconnecting }

private val DEG_TO_RAD = (PI / 180.0).toFloat()

/** Speed of the satellite's orbit while connecting. */
private const val ORBIT_DEG_PER_SEC = 150f

/**
 * How far the satellite swings toward the ring while connecting (0..1 of the gap).
 * It pulses in and out, so the circle reads as searching, not as stuck.
 */
private const val APPROACH_SHARE = 0.35f

/**
 * The orb's motion is built from ONE fusion value, not from separate restarting
 * animations:
 *
 *  - [fusion] is 0 when the satellite circle is apart and 1 when it has merged into
 *    the main ring. It only ever moves with animateTo, which starts from the current
 *    value. That is why it never jumps back: nothing snaps it except the reset to Off.
 *  - The satellite's orbit angle advances only while connecting and freezes otherwise.
 *    Freezing (not deleting) the satellite means leaving the connecting state never
 *    removes a composable mid-motion.
 *  - Recomposition does not restart anything. The effects are keyed on the state
 *    itself, and only the draw phase reads the animated values.
 */
@Composable
fun PowerOrb(
    state: OrbState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = Springs.Press,
        label = "orb-press",
    )

    val accent by animateColorAsState(
        targetValue = accentFor(state),
        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
        label = "orb-accent",
    )

    // Satellite is present while connecting, failing or disconnecting. Off hides it.
    val presence by animateFloatAsState(
        targetValue = if (state == OrbState.Off) 0f else 1f,
        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
        label = "orb-presence",
    )

    val fusionTarget = if (state == OrbState.Online) 1f else 0f
    val fusion = remember { Animatable(0f) }
    val orbit = remember { mutableFloatStateOf(0f) }
    val shake = remember { Animatable(0f) }

    // Fusion: one target, animated from wherever it currently is. Changing between
    // states that share a target (Connecting, Disconnecting, Off) does not restart it.
    LaunchedEffect(fusionTarget, reduce) {
        if (reduce) {
            fusion.snapTo(fusionTarget)
        } else if (fusionTarget == 1f) {
            fusion.animateTo(1f, Springs.Fuse)
        } else {
            fusion.animateTo(0f, tween(Motion.BASE, easing = Motion.Exit))
        }
    }

    // Orbit: advances only while connecting. Stopping keeps the last angle.
    val connecting = state == OrbState.Connecting
    LaunchedEffect(connecting, reduce) {
        if (connecting && !reduce) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    if (last >= 0L) {
                        orbit.floatValue =
                            (orbit.floatValue + (now - last) / 1_000_000_000f * ORBIT_DEG_PER_SEC) % 360f
                    }
                    last = now
                }
            }
        }
    }

    // Error: a single damped shake. It runs only when the state becomes Error.
    LaunchedEffect(state) {
        if (state == OrbState.Error && !reduce) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    0f at 0
                    -14f at 60
                    14f at 140
                    -8f at 220
                    6f at 300
                    0f at 420
                },
            )
        }
    }

    // Breathing exists only while connected, and is skipped for reduced motion.
    val breath: Float = if (state == OrbState.Online && !reduce) {
        val transition = rememberInfiniteTransition(label = "orb-breath")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "orb-breath-scale",
        )
        scale
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(252.dp)
            .graphicsLayer {
                translationX = shake.value
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Everything below reads animated values only inside the draw phase, so
        // animating does not recompose the tree.
        Canvas(Modifier.fillMaxSize()) {
            val m = fusion.value
            val radius = size.minDimension / 2f
            val ring = radius * 0.82f
            val outer = radius * 0.98f

            // The satellite swings toward the ring while connecting, then fusion takes it in.
            val angleDeg = orbit.floatValue
            val wobble = 0.5f - 0.5f * cos(angleDeg * DEG_TO_RAD * 2f)
            val approach = m + (1f - m) * APPROACH_SHARE * wobble
            val satelliteRadius = outer + (ring - outer) * approach
            val a = angleDeg * DEG_TO_RAD
            val sx = center.x + cos(a) * satelliteRadius
            val sy = center.y + kotlin.math.sin(a) * satelliteRadius

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.26f + 0.10f * m), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.18f + 0.22f * m),
                radius = ring,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            // The ring draws itself in as the satellite fuses, and draws back out on disconnect.
            if (m > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * m,
                    useCenter = false,
                    topLeft = Offset(center.x - ring, center.y - ring),
                    size = Size(ring * 2, ring * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val satelliteAlpha = (1f - m) * presence
            if (satelliteAlpha > 0.01f) {
                drawCircle(
                    color = accent.copy(alpha = satelliteAlpha),
                    radius = 8.dp.toPx(),
                    center = Offset(sx, sy),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(158.dp)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Palette.SurfaceRaised, Palette.Surface)))
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(58.dp),
            )
        }
    }
}

private fun accentFor(state: OrbState): Color = when (state) {
    OrbState.Off, OrbState.Disconnecting -> Palette.Idle
    OrbState.Connecting -> Palette.Connecting
    OrbState.Online -> Palette.Online
    OrbState.Error -> Palette.Error
}
