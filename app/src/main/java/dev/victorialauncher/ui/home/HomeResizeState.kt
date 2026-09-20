// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import dev.victorialauncher.data.HomePaddings
import kotlinx.coroutines.delay

internal enum class ResizeTarget { WIDGET, NOW_PLAYING }

/** A pan remains valid while only the size owned by this gesture changes beneath it. */
internal data class ResizePanAnchor(
    val windowSize: IntSize,
    val contentHeight: Int,
    val layoutIdentity: List<Any>,
    val initial: ResizePreview,
    val current: ResizePreview = initial,
) {
    fun matches(size: IntSize, height: Int, density: Float, identity: List<Any>): Boolean =
        windowSize == size && layoutIdentity == identity && kotlin.math.abs(height - (contentHeight +
            (current.height + current.topPadding - initial.height - initial.topPadding) * density)) <= 2f
}

/** Keep resize lifecycle separate from the large home layout's generated Compose method. */
@Stable
internal class HomeResizeState {
    var resizeTarget by mutableStateOf<ResizeTarget?>(null)
    var resizeDragging by mutableStateOf(false)
    var resizeActivity by mutableIntStateOf(0)
    var resizeBounds = Rect.Zero
    var resizeRootCoordinates: LayoutCoordinates? = null
    var widgetResizeMenu by mutableStateOf(false)
    var widgetMoreRequest by mutableIntStateOf(0)
    var widgetPreview by mutableStateOf<ResizePreview?>(null)
    var nowPlayingPreview by mutableStateOf<ResizePreview?>(null)
    var panAnchor: ResizePanAnchor? = null

    fun beginPan(size: IntSize, contentHeight: Int, value: ResizePreview, identity: List<Any>) {
        panAnchor = ResizePanAnchor(size, contentHeight, identity, value)
    }

    fun previewPan(value: ResizePreview) {
        panAnchor = panAnchor?.copy(current = value)
    }

    fun cancelPan() {
        panAnchor = panAnchor?.let { it.copy(current = it.initial) }
    }
}

@Composable
internal fun rememberHomeResizeState(
    editMode: Boolean,
    hasWidget: Boolean,
    nowPlayingHasContent: Boolean,
    widgetHeightDp: Int,
    nowPlayingHeightDp: Int,
    paddings: HomePaddings,
    nowPlayingMenu: Boolean,
    onModeChanged: (Boolean) -> Unit,
): HomeResizeState {
    val state = remember { HomeResizeState() }
    LaunchedEffect(state.resizeTarget) { onModeChanged(state.resizeTarget != null) }
    DisposableEffect(Unit) { onDispose { onModeChanged(false) } }
    BackHandler(state.resizeTarget != null) { state.resizeTarget = null }
    LaunchedEffect(state.resizeTarget, state.resizeDragging, state.widgetResizeMenu, nowPlayingMenu, state.resizeActivity) {
        if (state.resizeTarget != null && !state.resizeDragging && !state.widgetResizeMenu && !nowPlayingMenu) {
            delay(3_000L)
            state.resizeTarget = null
        }
    }
    LaunchedEffect(editMode, hasWidget, nowPlayingHasContent) {
        if (editMode || (state.resizeTarget == ResizeTarget.WIDGET && !hasWidget) ||
            (state.resizeTarget == ResizeTarget.NOW_PLAYING && !nowPlayingHasContent)) state.resizeTarget = null
    }
    LaunchedEffect(widgetHeightDp, paddings.widgetTop, state.widgetPreview, state.resizeDragging) {
        if (!state.resizeDragging && state.widgetPreview == ResizePreview(widgetHeightDp, paddings.widgetTop)) {
            state.widgetPreview = null
        }
    }
    LaunchedEffect(nowPlayingHeightDp, paddings.nowPlayingTop, state.nowPlayingPreview, state.resizeDragging) {
        if (!state.resizeDragging && state.nowPlayingPreview == ResizePreview(nowPlayingHeightDp, paddings.nowPlayingTop)) {
            state.nowPlayingPreview = null
        }
    }
    return state
}
