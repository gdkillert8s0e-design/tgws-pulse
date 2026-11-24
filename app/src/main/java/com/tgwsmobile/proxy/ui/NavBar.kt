package com.tgwsmobile.proxy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tgwsmobile.proxy.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/** One action shown in the expanded dock. [enabled] false keeps it visible but inert. */
data class QuickAction(val label: String, val enabled: Boolean, val onClick: () -> Unit)

private data class DockItem(val label: Int, val icon: ImageVector)

private val DOCK_ITEMS = listOf(
    DockItem(R.string.nav_proxy, Icons.Rounded.PowerSettingsNew),
    DockItem(R.string.nav_settings, Icons.Rounded.Settings),
    DockItem(R.string.nav_diagnostics, Icons.Rounded.BugReport),
)

private val TAB_SIZE = 56.dp
private val TAB_GAP = 4.dp
private val DOCK_PAD = 6.dp
private val DOCK_COLLAPSED_H = TAB_SIZE + DOCK_PAD * 2
private val DOCK_COLLAPSED_W = TAB_SIZE * 3 + TAB_GAP * 2 + DOCK_PAD * 2
private val DOCK_EXPANDED_W = 340.dp
private val DOCK_EXPANDED_H = 260.dp

/**
 * Floating Rubber Dock. A compact pill at the bottom that:
 *  - moves a highlight between tabs with two springs: the leading edge is stiff and
 *    arrives first, the trailing edge is soft and catches up, so the highlight
 *    stretches like rubber in between;
 *  - inverts the active icon by clipping a second, dark icon layer to the highlight;
 *  - pops the active icon with a spring;
 *  - opens into a card when the active tab is tapped, and the card's actions enter
 *    one after another; tapping the active tab again (or outside, or Back) closes it.
 *
 * The dock is drawn as an overlay over the content, so opening it never reflows the
 * page underneath.
 */
@Composable
fun RubberDock(
    selected: Int,
    expanded: Boolean,
    titles: List<String>,
    actionsFor: (Int) -> List<QuickAction>,
    onSelect: (Int) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val tabPx = with(density) { TAB_SIZE.toPx() }
    val gapPx = with(density) { TAB_GAP.toPx() }
    val padPx = with(density) { DOCK_PAD.toPx() }
    fun tabCenter(i: Int): Float = padPx + tabPx / 2f + i * (tabPx + gapPx)

    val left = remember { Animatable(tabCenter(selected) - tabPx / 2f) }
    val right = remember { Animatable(tabCenter(selected) + tabPx / 2f) }

    LaunchedEffect(selected) {
        val target = tabCenter(selected)
        val leftTarget = target - tabPx / 2f
        val rightTarget = target + tabPx / 2f
        val movingRight = target > (left.value + right.value) / 2f
        if (reduce) {
            left.snapTo(leftTarget)
            right.snapTo(rightTarget)
        } else {
            coroutineScope {
                // The edge facing the direction of travel leads; the other one trails.
                launch {
                    if (movingRight) right.animateTo(rightTarget, Springs.LeadEdge)
                    else left.animateTo(leftTarget, Springs.LeadEdge)
                }
                launch {
                    if (movingRight) left.animateTo(leftTarget, Springs.TrailEdge)
                    else right.animateTo(rightTarget, Springs.TrailEdge)
                }
            }
        }
    }

    val dockWidth by animateDpAsState(
        targetValue = if (expanded) DOCK_EXPANDED_W else DOCK_COLLAPSED_W,
        animationSpec = if (reduce) snap<Dp>() else Springs.MorphDp,
        label = "dock-width",
    )
    val dockHeight by animateDpAsState(
        targetValue = if (expanded) DOCK_EXPANDED_H else DOCK_COLLAPSED_H,
        animationSpec = if (reduce) snap<Dp>() else Springs.MorphDp,
        label = "dock-height",
    )
    val corner by animateDpAsState(
        targetValue = if (expanded) 28.dp else DOCK_COLLAPSED_H / 2,
        animationSpec = if (reduce) snap<Dp>() else Springs.MorphDp,
        label = "dock-corner",
    )

    val scales = DOCK_ITEMS.mapIndexed { i, _ ->
        animateFloatAsState(
            targetValue = if (i == selected) 1.1f else 1f,
            animationSpec = Springs.Pop,
            label = "tab-scale-$i",
        ).value
    }

    Box(
        modifier = modifier
            .width(dockWidth)
            .height(dockHeight)
            .clip(RoundedCornerShape(corner))
            .background(Palette.SurfaceRaised)
            .border(1.dp, Palette.Outline, RoundedCornerShape(corner)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ExpandedPanel(expanded = expanded, selected = selected, titles = titles, actionsFor = actionsFor)
            }

            Box(Modifier.fillMaxWidth().height(DOCK_COLLAPSED_H)) {
                // Layer 0: the highlight, drawn in the draw phase from the two edges.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val (l, r, h, top) = highlightGeometry(left.value, right.value, size.height, tabPx)
                            drawRoundRect(
                                color = Palette.TextPrimary,
                                topLeft = Offset(l, top),
                                size = Size(r - l, h),
                                cornerRadius = CornerRadius(h / 2f),
                            )
                        },
                )

                // Layer 1: muted icons, and the tap targets.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = DOCK_PAD),
                    horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DOCK_ITEMS.forEachIndexed { i, item ->
                        Box(
                            Modifier
                                .size(TAB_SIZE)
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) {
                                    if (i == selected) onToggleExpanded() else onSelect(i)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DockIcon(item = item, scale = scales[i], tint = Palette.TextMuted)
                        }
                    }
                }

                // Layer 2: dark icons, drawn only inside the highlight. Touches pass through it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val (l, r, h, top) = highlightGeometry(left.value, right.value, size.height, tabPx)
                            val path = Path().apply {
                                addRoundRect(RoundRect(l, top, r, top + h, CornerRadius(h / 2f)))
                            }
                            drawContext.canvas.save()
                            drawContext.canvas.clipPath(path)
                            drawContent()
                            drawContext.canvas.restore()
                        },
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = DOCK_PAD),
                        horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DOCK_ITEMS.forEachIndexed { i, item ->
                            Box(Modifier.size(TAB_SIZE), contentAlignment = Alignment.Center) {
                                DockIcon(item = item, scale = scales[i], tint = Palette.Background)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Left, right, height and top of the highlight. Height keeps its area as the width changes. */
private data class HighlightGeometry(val l: Float, val r: Float, val h: Float, val top: Float)

private fun highlightGeometry(a: Float, b: Float, containerH: Float, tabPx: Float): HighlightGeometry {
    val l = minOf(a, b)
    val r = max(a, b)
    val width = max(r - l, 1f)
    val h = tabPx * (1f / sqrt(width / tabPx)).coerceIn(0.7f, 1.08f)
    return HighlightGeometry(l, r, h, (containerH - h) / 2f)
}

@Composable
private fun DockIcon(item: DockItem, scale: Float, tint: Color) {
    Icon(
        imageVector = item.icon,
        contentDescription = stringResource(item.label),
        tint = tint,
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

/** The card the dock grows into. Content is keyed by tab, so switching tabs crossfades it. */
@Composable
private fun ExpandedPanel(
    expanded: Boolean,
    selected: Int,
    titles: List<String>,
    actionsFor: (Int) -> List<QuickAction>,
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(Motion.BASE, delayMillis = 60, easing = Motion.Standard)),
        exit = fadeOut(tween(Motion.FAST)),
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(tween(Motion.BASE, easing = Motion.Standard)) togetherWith
                    fadeOut(tween(Motion.FAST))
            },
            label = "dock-actions",
        ) { index ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = titles.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextMuted,
                )
                actionsFor(index).forEachIndexed { i, action ->
                    StaggeredAction(index = i, action = action)
                }
            }
        }
    }
}

/**
 * Each action enters a little after the previous one. The state lives in this
 * composable, so the stagger replays whenever the card's content is created.
 */
@Composable
private fun StaggeredAction(index: Int, action: QuickAction) {
    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    val delay = 90 + index * Motion.STAGGER
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(Motion.BASE, delayMillis = delay, easing = Motion.Standard)) +
            slideInVertically(tween(Motion.BASE, delayMillis = delay, easing = Motion.Expressive)) { it / 2 },
    ) {
        ActionRow(action)
    }
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
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
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
            modifier = Modifier.padding(start = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.enabled) Palette.TextPrimary else Palette.TextMuted,
        )
    }
}
