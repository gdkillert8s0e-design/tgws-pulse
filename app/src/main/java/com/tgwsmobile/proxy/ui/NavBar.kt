package com.tgwsmobile.proxy.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tgwsmobile.proxy.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** One action shown in the expanded dock. [enabled] false keeps it visible but inert. */
data class QuickAction(val label: String, val enabled: Boolean, val onClick: () -> Unit)

private data class DockItem(val label: Int, val icon: ImageVector)

private val DOCK_ITEMS = listOf(
    DockItem(R.string.nav_proxy, Icons.Rounded.PowerSettingsNew),
    DockItem(R.string.nav_settings, Icons.Rounded.Settings),
    DockItem(R.string.nav_diagnostics, Icons.Rounded.BugReport),
)

// Compact, low, floating. Three tabs of 46dp with 8dp gaps and 8dp side padding.
private val TAB = 46.dp
private val GAP = 8.dp
private val PAD = 8.dp
private val DOCK_W = TAB * 3 + GAP * 2 + PAD * 2
private val DOCK_H = TAB + 14.dp
private val STRETCH_MAX = 12.dp

/**
 * Floating glass dock with a draggable indicator.
 *
 *  - Tap a tab: selects it. Tap the active tab: opens the quick card.
 *  - Press and hold the white indicator: it lifts (scale, glow, haptic). Drag it to
 *    any tab. It follows the finger on a spring, stretches with speed, and on release
 *    settles onto the nearest tab. The glass panel itself never moves.
 *
 * The dock is an overlay, so opening the card never reflows the page underneath.
 */
@Composable
fun RubberDock(
    selected: Int,
    titles: List<String>,
    actionsFor: (Int) -> List<QuickAction>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()

    val tabPx = with(density) { TAB.toPx() }
    val gapPx = with(density) { GAP.toPx() }
    val padPx = with(density) { PAD.toPx() }
    val stretchMaxPx = with(density) { STRETCH_MAX.toPx() }
    fun centerPx(i: Int): Float = padPx + tabPx / 2f + i * (tabPx + gapPx)
    val firstCenter = centerPx(0)
    val lastCenter = centerPx(2)

    // Thumb = the centre of the white indicator, in pixels from the dock's left edge.
    val thumb = remember { Animatable(centerPx(selected)) }
    val lift = remember { Animatable(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableFloatStateOf(centerPx(selected)) }

    // A selection (tap, or a settled drag) pulls the thumb onto its tab.
    LaunchedEffect(selected) {
        if (!dragging) {
            if (reduce) thumb.snapTo(centerPx(selected)) else thumb.animateTo(centerPx(selected), Springs.Settle)
        }
    }
    // While the finger is down, the thumb follows the target on a close spring.
    LaunchedEffect(dragging, dragTarget) {
        if (dragging && !reduce) thumb.animateTo(dragTarget, Springs.Follow)
        else if (dragging) thumb.snapTo(dragTarget)
    }
    LaunchedEffect(dragging) {
        lift.animateTo(if (dragging) 1.12f else 1f, Springs.Press)
    }

    val dockWidth = DOCK_W
    val dockHeight = DOCK_H
    val corner = DOCK_H / 2

    val scales = DOCK_ITEMS.mapIndexed { i, _ ->
        animateFloatAsState(
            targetValue = if (i == selected) 1.08f else 1f,
            animationSpec = Springs.Pop,
            label = "tab-scale-$i",
        ).value
    }

    // Gesture callbacks read the latest values without restarting the pointer handler.
    val selectedNow by rememberUpdatedState(selected)
    val onSelectNow by rememberUpdatedState(onSelect)


    fun indexAt(x: Float): Int? {
        val i = floor((x - padPx) / (tabPx + gapPx)).toInt()
        if (i !in 0..2) return null
        val left = padPx + i * (tabPx + gapPx)
        return if (x in left..(left + tabPx)) i else null
    }
    fun nearestIndex(x: Float): Int =
        ((x - padPx - tabPx / 2f) / (tabPx + gapPx)).roundToInt().coerceIn(0, 2)

    val indicatorColor = if (Palette.neon) Palette.Online else Palette.TextPrimary
    val glassFill = Palette.SurfaceRaised.copy(alpha = if (Palette.neon) 0.62f else 0.70f)
    val glassRim = Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .width(dockWidth)
            .height(dockHeight)
            .clip(RoundedCornerShape(corner))
            .background(glassFill)
            .border(1.dp, glassRim, RoundedCornerShape(corner)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {}

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DOCK_H)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val startX = down.position.x
                            val pressedAt = SystemClock.uptimeMillis()
                            // The press counts as a hold only when it starts on the white indicator.
                            val onThumb = abs(startX - thumb.value) <= tabPx / 2f + gapPx / 2f
                            var lifted = false
                            var moved = false
                            var up: PointerInputChange? = null

                            while (up == null) {
                                val holdable = !lifted && !moved && onThumb
                                val event: PointerEvent? = if (holdable) {
                                    val remaining = viewConfig.longPressTimeoutMillis - (SystemClock.uptimeMillis() - pressedAt)
                                    if (remaining > 0) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null
                                } else {
                                    awaitPointerEvent()
                                }

                                if (event == null) {
                                    // Hold reached: lift the indicator and answer with a haptic.
                                    lifted = true
                                    dragTarget = thumb.value
                                    dragging = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    continue
                                }

                                val change = event.changes.first()
                                if (change.changedToUp()) {
                                    up = change
                                    continue
                                }
                                if (!lifted) {
                                    if (abs(change.position.x - startX) > viewConfig.touchSlop) moved = true
                                } else {
                                    change.consume()
                                    dragTarget = change.position.x.coerceIn(firstCenter, lastCenter)
                                }
                            }

                            if (lifted) {
                                dragging = false
                                val target = nearestIndex(dragTarget)
                                if (target == selectedNow) {
                                    scope.launch { thumb.animateTo(centerPx(target), Springs.Settle) }
                                }
                                onSelectNow(target)
                            } else if (!moved) {
                                val index = indexAt(startX)
                                if (index != null) {
                                    onSelectNow(index)
                                }
                            }
                        }
                    },
            ) {
                // Layer 0: the white (or pink) indicator with rubber stretch and a lift glow.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val pill = pillFor(thumb.value, thumb.velocity, lift.value, tabPx, size.height, stretchMaxPx)
                            val liftAmount = ((lift.value - 1f) / 0.12f).coerceIn(0f, 1f)
                            if (liftAmount > 0f) {
                                val g = 6.dp.toPx()
                                drawRoundRect(
                                    color = indicatorColor.copy(alpha = 0.28f * liftAmount),
                                    topLeft = Offset(pill.left - g, pill.top - g),
                                    size = Size(pill.width + g * 2, pill.height + g * 2),
                                    cornerRadius = CornerRadius((pill.height + g * 2) / 2f),
                                )
                            }
                            drawRoundRect(
                                color = indicatorColor,
                                topLeft = Offset(pill.left, pill.top),
                                size = Size(pill.width, pill.height),
                                cornerRadius = CornerRadius(pill.height / 2f),
                            )
                        },
                )

                // Layer 1: muted icons and the tap targets.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = PAD),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DOCK_ITEMS.forEachIndexed { i, item ->
                        Box(Modifier.size(TAB), contentAlignment = Alignment.Center) {
                            DockIcon(item = item, scale = scales[i], tint = Palette.TextMuted)
                        }
                    }
                }

                // Layer 2: dark icons, drawn only inside the indicator. Touches pass through.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val pill = pillFor(thumb.value, thumb.velocity, lift.value, tabPx, size.height, stretchMaxPx)
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(pill.left, pill.top, pill.left + pill.width, pill.top + pill.height,
                                        CornerRadius(pill.height / 2f))
                                )
                            }
                            drawContext.canvas.save()
                            drawContext.canvas.clipPath(path)
                            drawContent()
                            drawContext.canvas.restore()
                        },
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = PAD),
                        horizontalArrangement = Arrangement.spacedBy(GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DOCK_ITEMS.forEachIndexed { i, item ->
                            Box(Modifier.size(TAB), contentAlignment = Alignment.Center) {
                                DockIcon(item = item, scale = scales[i], tint = Palette.Background)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Geometry of the indicator: its width grows with thumb speed (rubber), and it scales while lifted. */
private data class Pill(val left: Float, val top: Float, val width: Float, val height: Float)

private fun pillFor(
    center: Float,
    velocity: Float,
    lift: Float,
    tabPx: Float,
    containerH: Float,
    stretchMaxPx: Float,
): Pill {
    val stretch = (abs(velocity) / 2500f).coerceIn(0f, 1f) * stretchMaxPx
    val width = (tabPx * 0.92f + stretch) * lift
    val height = tabPx * 0.92f * lift
    return Pill(left = center - width / 2f, top = (containerH - height) / 2f, width = width, height = height)
}

@Composable
private fun DockIcon(item: DockItem, scale: Float, tint: Color) {
    Icon(
        imageVector = item.icon,
        contentDescription = stringResource(item.label),
        tint = tint,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
private fun ActionRow(action: QuickAction) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && action.enabled) 0.97f else 1f,
        animationSpec = Springs.Press,
        label = "action-press",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (action.enabled) Palette.Surface else Palette.SurfaceRaised)
            .clickable(
                enabled = action.enabled,
                interactionSource = interaction,
                indication = null,
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = action.label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.enabled) Palette.TextPrimary else Palette.TextMuted,
        )
    }
}
