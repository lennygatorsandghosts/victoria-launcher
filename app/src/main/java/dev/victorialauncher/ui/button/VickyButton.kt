// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ButtonAction
import dev.victorialauncher.data.ButtonSlot
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.ui.common.AppIcon
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private val BUTTON_SIZE = 56.dp
private val BUTTON_BOTTOM_INSET = 16.dp
private val BUTTON_EDGE_INSET = 16.dp
private val BUTTON_STRIP_INSET = 72.dp
private val SWIPE_THRESHOLD = 48.dp

fun classifySwipe(dx: Float, dy: Float, thresholdPx: Float): ButtonSlot? {
    val absX = abs(dx)
    val absY = abs(dy)
    if (absX <= thresholdPx && absY <= thresholdPx) return null
    return if (absY >= absX) {
        if (dy < -thresholdPx) ButtonSlot.SWIPE_UP else null
    } else {
        when {
            dx < -thresholdPx -> ButtonSlot.SWIPE_LEFT
            dx > thresholdPx -> ButtonSlot.SWIPE_RIGHT
            else -> null
        }
    }
}

@Composable
fun VickyButton(
    enabled: Boolean,
    homeEditMode: Boolean,
    appListVisible: Boolean,
    edgeSide: EdgeSide,
    tapAction: ButtonAction,
    resolveEntry: (String) -> AppInfo?,
    hapticsEnabled: Boolean,
    onTapAction: () -> Unit,
    onSwipeAction: (ButtonSlot) -> Unit,
    onFocusAppSearch: () -> Unit,
    onOpenEditSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled || homeEditMode) return

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val currentTapAction by rememberUpdatedState(onTapAction)
    val currentSwipeAction by rememberUpdatedState(onSwipeAction)
    val currentFocusAppSearch by rememberUpdatedState(onFocusAppSearch)
    val currentOpenEditSheet by rememberUpdatedState(onOpenEditSheet)

    val endInset = if (edgeSide != EdgeSide.LEFT) BUTTON_STRIP_INSET else BUTTON_EDGE_INSET
    val thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }

    FloatingActionButton(
        onClick = {},
        shape = CircleShape,
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = endInset, bottom = BUTTON_BOTTOM_INSET)
            .size(BUTTON_SIZE)
            .semantics { contentDescription = "Vicky+ button" }
            .pointerInput(thresholdPx, hapticsEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull ButtonGesture.Cancel
                            if (!change.pressed) return@withTimeoutOrNull ButtonGesture.Tap

                            val delta = change.positionChange()
                            dx += delta.x
                            dy += delta.y

                            val swipe = classifySwipe(dx, dy, thresholdPx)
                            if (swipe != null) {
                                change.consume()
                                currentSwipeAction(swipe)
                                return@withTimeoutOrNull ButtonGesture.Fired
                            }
                            if (dy > thresholdPx && abs(dy) >= abs(dx)) {
                                change.consume()
                                return@withTimeoutOrNull ButtonGesture.Fired
                            }
                        }
                    }

                    when (result) {
                        ButtonGesture.Tap -> {
                            when {
                                imeVisible -> {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }
                                appListVisible -> currentFocusAppSearch()
                                else -> currentTapAction()
                            }
                        }
                        ButtonGesture.Fired, ButtonGesture.Cancel -> Unit
                        null -> {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOpenEditSheet()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { if (it.id == down.id) it.consume() }
                            } while (event.changes.any { it.id == down.id && it.pressed })
                        }
                    }
                }
            },
    ) {
        ButtonGlyph(
            imeVisible = imeVisible,
            appListVisible = appListVisible,
            tapAction = tapAction,
            resolveEntry = resolveEntry,
        )
    }
}

@Composable
private fun ButtonGlyph(
    imeVisible: Boolean,
    appListVisible: Boolean,
    tapAction: ButtonAction,
    resolveEntry: (String) -> AppInfo?,
) {
    when {
        imeVisible -> Icon(Icons.Filled.KeyboardHide, contentDescription = null)
        appListVisible -> Icon(Icons.Filled.Search, contentDescription = null)
        tapAction is ButtonAction.LaunchEntry -> {
            val entry = resolveEntry(tapAction.key)
            if (entry != null) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(app = entry, sizeDp = 28)
                }
            } else {
                Icon(Icons.Filled.LinkOff, contentDescription = null)
            }
        }
        else -> Icon(buttonIcon(tapAction), contentDescription = null)
    }
}

private fun buttonIcon(action: ButtonAction): ImageVector = when (action) {
    ButtonAction.None -> Icons.Filled.LinkOff
    is ButtonAction.LaunchEntry -> Icons.Filled.LinkOff
    ButtonAction.WebSearch -> Icons.Filled.Search
    ButtonAction.SearchApps -> Icons.Filled.Search
    ButtonAction.Notifications -> Icons.Filled.Notifications
    ButtonAction.LockScreen -> Icons.Filled.Lock
    ButtonAction.LauncherSettings -> Icons.Filled.Settings
    ButtonAction.TogglePrivateSpace -> Icons.Filled.Security
    is ButtonAction.OpenUrl -> Icons.Filled.Link
}

private enum class ButtonGesture { Tap, Fired, Cancel }
