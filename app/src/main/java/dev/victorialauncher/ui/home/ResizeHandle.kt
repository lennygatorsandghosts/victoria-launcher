// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import kotlin.math.roundToInt

/** Both values describe one layout and are persisted together when the gesture ends. */
data class ResizePreview(val height: Int, val topPadding: Int)

/**
 * Coordinates are in the window, like HomeSafeArea. A drag snapshots the actual rendered
 * edge once; every move is measured from the pointer's absolute position. The moving handle's
 * local deltas must never be accumulated, since its own movement changes that coordinate space.
 */
private fun resizeFromEdge(
    initial: ResizePreview,
    bounds: Rect,
    top: Boolean,
    deltaPx: Float,
    density: Float,
    range: IntRange,
    safeTop: Float,
    safeBottom: Float,
): ResizePreview {
    val requested = initial.height + ((if (top) -deltaPx else deltaPx) / density).roundToInt()
    val minimum = if (top) maxOf(range.first, initial.height + initial.topPadding - 400) else range.first
    val maximum = if (top) {
        minOf(range.last, initial.height + initial.topPadding,
            initial.height + ((bounds.top - safeTop) / density).toInt())
    } else {
        minOf(range.last, initial.height + ((safeBottom - bounds.bottom) / density).toInt())
    // A scroll may already leave this edge outside the safe viewport while part of its
    // handle remains reachable. Do not repair that scroll position with a zero-distance
    // resize; allow inward movement, and prevent further growth past the starting edge.
    }.coerceAtLeast(initial.height).coerceAtLeast(minimum)
    val height = requested.coerceIn(minimum, maximum)
    return ResizePreview(height, if (top) initial.topPadding + initial.height - height else initial.topPadding)
}

/** Shared controls for a hosted widget and the Now Playing card. */
@Composable
fun BoxScope.ResizeOverlay(
    value: () -> ResizePreview,
    range: IntRange,
    safeTop: Float,
    safeBottom: Float,
    onBounds: (Rect) -> Unit,
    onPreview: (ResizePreview) -> Unit,
    onCommit: (ResizePreview) -> Unit,
    onCancel: () -> Unit,
    onDragging: (Boolean) -> Unit,
    onMore: () -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { bounds = it.boundsInWindow(); onBounds(bounds) }
            .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
    )
    for (top in listOf(true, false)) {
        ResizeHandle(
            top = top,
            modifier = Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                .size(width = 64.dp, height = (range.first / 2).dp),
            onStart = {
                val initial = value()
                val initialBounds = bounds
                onDragging(true)
                // Immutable baseline for the entire gesture, including its first movement.
                val calculate: (Float) -> ResizePreview = { delta ->
                    resizeFromEdge(initial, initialBounds, top, delta, density, range, safeTop, safeBottom)
                }
                calculate
            },
            onPreview = onPreview,
            onCommit = onCommit,
            onCancel = onCancel,
            onEnd = { onDragging(false) },
            onStep = { increase ->
                onDragging(true)
                val delta = density * (if (increase) 1 else -1) * (if (top) -1 else 1)
                val next = resizeFromEdge(value(), bounds, top, delta, density, range, safeTop, safeBottom)
                onPreview(next)
                onCommit(next)
                onDragging(false)
            },
        )
    }
    val more = stringResource(R.string.resize_more)
    TextButton(onClick = onMore, modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = more }) {
        Text(more, color = Color.White)
    }
}

@Composable
private fun ResizeHandle(
    top: Boolean,
    modifier: Modifier,
    onStart: () -> ((Float) -> ResizePreview),
    onPreview: (ResizePreview) -> Unit,
    onCommit: (ResizePreview) -> Unit,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
    onStep: (Boolean) -> Unit,
) {
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val start by rememberUpdatedState(onStart)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val end by rememberUpdatedState(onEnd)
    val description = stringResource(if (top) R.string.resize_widget_top else R.string.resize_widget_bottom)
    val increase = stringResource(R.string.resize_increase)
    val decrease = stringResource(R.string.resize_decrease)
    Box(
        // Each control occupies at most half the element's minimum height, so the
        // top and bottom touch regions remain distinct even on the smallest block.
        modifier
            .onGloballyPositioned { coordinates = it }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(increase) { onStep(true); true },
                    CustomAccessibilityAction(decrease) { onStep(false); true },
                )
            }
            .pointerInput(top) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = coordinates?.localToWindow(down.position) ?: return@awaitEachGesture
                    down.consume()
                    val calculate = start()
                    var committed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Compose sends a consumed synthetic up when Android cancels the
                            // stream. Only an unconsumed real release may persist a resize.
                            val released = change.changedToUp()
                            if (change.isConsumed || (!change.pressed && !released)) break
                            val position = coordinates?.localToWindow(change.position) ?: break
                            val result = calculate(position.y - origin.y)
                            preview(result)
                            change.consume()
                            if (released) {
                                // No DataStore writes occur in the movement loop.
                                commit(result)
                                committed = true
                                break
                            }
                        }
                    } finally {
                        if (!committed) cancel()
                        end()
                    }
                }
            },
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp)
            .background(Color.White, RoundedCornerShape(3.dp)))
    }
}
