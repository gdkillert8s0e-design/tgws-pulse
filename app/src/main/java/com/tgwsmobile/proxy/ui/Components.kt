package com.tgwsmobile.proxy.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

/** The corner shape used by cards and pills, from the button/card style settings. */
val CardShape: RoundedCornerShape
    get() = when (Palette.cardStyle) {
        CardStyle.Soft -> RoundedCornerShape(18.dp)
        CardStyle.Raised -> RoundedCornerShape(24.dp)
        CardStyle.Sharp -> RoundedCornerShape(4.dp)
        CardStyle.Glass -> RoundedCornerShape(28.dp)
    }

val PillShape: RoundedCornerShape
    get() = when (Palette.buttonStyle) {
        ButtonStyle.Pill -> RoundedCornerShape(27.dp)
        ButtonStyle.Rounded -> RoundedCornerShape(14.dp)
        ButtonStyle.Outline -> RoundedCornerShape(10.dp)
        ButtonStyle.Glow -> RoundedCornerShape(27.dp)
    }

/** A calm surface card with a hairline border; the only container shape used in the app. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Palette.Surface)
            .border(
                width = if (Palette.cardStyle == CardStyle.Glass) 1.dp else 1.dp,
                brush = if (Palette.isGlass) {
                    Brush.horizontalGradient(listOf(Palette.Outline, Color.Transparent, Palette.Outline))
                } else {
                    androidx.compose.ui.graphics.SolidColor(Palette.Outline)
                },
                shape = CardShape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

/**
 * Pill button with a spring press response. Scales slightly under the finger and
 * settles with a little overshoot on release.
 */
@Composable
fun PressablePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "pill-press",
    )
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Palette.SurfaceRaised
            primary -> Palette.Online
            else -> Palette.SurfaceRaised
        },
        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
        label = "pill-bg",
    )
    val content = when {
        !enabled -> Palette.TextMuted
        primary -> Palette.Background
        else -> Palette.TextPrimary
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(54.dp)
            .then(
                if (Palette.buttonStyle == ButtonStyle.Glow && enabled) {
                    Modifier.pulseGlow(if (primary) Palette.Online else Palette.Connecting, alpha = Palette.glow)
                } else {
                    Modifier
                }
            )
            .clip(PillShape)
            .then(
                if (Palette.buttonStyle == ButtonStyle.Outline && !primary) {
                    Modifier.border(1.dp, Palette.Outline, PillShape)
                } else {
                    Modifier
                }
            )
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A label above a value, with the value in monospace. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, letterSpacing = 0.sp),
            color = Palette.TextPrimary,
        )
    }
}

/** Keeps a short accent tint readable on any surface. */
fun Color.tint(alpha: Float): Color = copy(alpha = alpha)
