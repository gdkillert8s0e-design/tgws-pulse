package com.tgwsmobile.proxy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Soft outer glow used by the orb, the launch overlay and glow-styled buttons. */
fun Modifier.pulseGlow(color: Color, alpha: Float = 0.5f): Modifier = this.then(
    Modifier.drawBehind {
        val radius = size.minDimension * 0.8f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.45f * alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            alpha = 1f,
        )
    }
)

/**
 * The one-time launch overlay. Six looks plus Off; nothing plays when
 * [LocalReduceMotion] is true or MotionState is off.
 */
@Composable
fun LaunchOverlay(anim: LaunchAnim, onDone: () -> Unit) {
    if (anim == LaunchAnim.Off) {
        onDone()
        return
    }
    var visible by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(anim) {
        progress.animateTo(1f, tween(MotionState.scaled(900), easing = LinearEasing))
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(MotionState.scaled(300)))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Palette.Background, Palette.BackgroundAlt))
                ),
            contentAlignment = Alignment.Center,
        ) {
            val p = progress.value
            val accent = Palette.Online
            when (anim) {
                LaunchAnim.Fade -> Box(
                    Modifier
                        .size(96.dp)
                        .alpha(p)
                        .pulseGlow(accent, alpha = Palette.glow)
                        .background(accent, shape = LaunchShape),
                )
                LaunchAnim.Reveal -> Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.6f * Palette.glow),
                        radius = size.minDimension * p,
                    )
                }
                LaunchAnim.NeonPulse -> repeat(3) { i ->
                    val phase = (p * 1.5f - i * 0.2f).coerceIn(0f, 1f)
                    Canvas(
                        Modifier
                            .align(Alignment.Center)
                            .size((80 + i * 44).dp)
                            .alpha(phase * (1f - phase) * 2f),
                    ) {
                        drawCircle(color = accent, radius = size.minDimension / 2, style = Stroke(2.dp.toPx()))
                    }
                }
                LaunchAnim.Scan -> {
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height * p
                        drawLine(
                            color = accent.copy(alpha = 0.8f * Palette.glow),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    Box(
                        Modifier
                            .size(64.dp)
                            .pulseGlow(accent, alpha = Palette.glow)
                            .background(Palette.SurfaceRaised, shape = LaunchShape),
                    )
                }
                LaunchAnim.Stagger -> Row {
                    listOf(Palette.Online, Palette.Connecting, Palette.TextPrimary).forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .padding(6.dp)
                                .size(20.dp)
                                .alpha(((p * 3f - i).coerceIn(0f, 1f)))
                                .background(c, shape = LaunchShape),
                        )
                    }
                }
                LaunchAnim.Quick, LaunchAnim.Off -> Unit
                else -> Unit
            }
        }
    }
    // The overlay must never block the app: report done as soon as the fade-out starts.
    LaunchedEffect(visible) { if (!visible) onDone() }
}

private val LaunchShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
