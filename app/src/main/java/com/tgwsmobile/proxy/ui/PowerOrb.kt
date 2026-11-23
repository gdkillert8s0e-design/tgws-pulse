package com.tgwsmobile.proxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

enum class OrbState { Off, Connecting, Online, Error }

/**
 * The main control. Each state has its own motion language:
 *  - Off: a still ring, no animation running at all.
 *  - Connecting: a comet sweeps around the ring and a faint echo expands.
 *  - Online: the ring draws itself once, then the core breathes very slowly.
 *  - Error: a short damped shake, then rest.
 * Infinite animations exist only while their state is visible, and none run when
 * the system asks to reduce motion.
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
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "orb-press",
    )

    val accent by animateColorAsState(
        targetValue = when (state) {
            OrbState.Off -> Palette.Idle
            OrbState.Connecting -> Palette.Connecting
            OrbState.Online -> Palette.Online
            OrbState.Error -> Palette.Error
        },
        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
        label = "orb-accent",
    )

    val reveal = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == OrbState.Online && !reduce) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(Motion.SLOW, easing = Motion.Expressive))
        }
    }

    val shake = remember { Animatable(0f) }
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

    val breath: Float = if (state == OrbState.Online && !reduce) {
        val transition = rememberInfiniteTransition(label = "orb-breath")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
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
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val ring = radius * 0.82f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = ring,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            if (state == OrbState.Online) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * reveal.value,
                    useCenter = false,
                    topLeft = Offset(center.x - ring, center.y - ring),
                    size = Size(ring * 2, ring * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        if (state == OrbState.Connecting && !reduce) {
            ConnectingSweep(accent)
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

/** Only composed while connecting: a rotating comet plus an expanding echo ring. */
@Composable
private fun ConnectingSweep(accent: Color) {
    val transition = rememberInfiniteTransition(label = "orb-connect")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "orb-sweep",
    )
    val echo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = Motion.Standard), RepeatMode.Restart),
        label = "orb-echo",
    )
    Canvas(Modifier.matchParentSize()) {
        val radius = size.minDimension / 2f
        val ring = radius * 0.82f
        drawCircle(
            color = accent.copy(alpha = (1f - echo) * 0.45f),
            radius = ring * (1f + 0.22f * echo),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawArc(
            color = accent,
            startAngle = sweep,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(center.x - ring, center.y - ring),
            size = Size(ring * 2, ring * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
