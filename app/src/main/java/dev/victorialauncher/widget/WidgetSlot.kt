// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.ui.common.TouchAnchoredMenu
import kotlinx.coroutines.delay
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** How long the page dots stay up after a swipe settles. */
private const val DOTS_LINGER_MS = 1200L

data class WidgetSlotActions(
    val onAddWidget: () -> Unit,
    val onRemoveWidget: (widgetId: Int) -> Unit,
    val onWidgetSettings: (widgetId: Int) -> Unit,
    val onAppInfo: (widgetId: Int) -> Unit,
    val onResize: (heightDp: Int) -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * The home screen's widget area: one widget on screen at a time, swiped between.
 *
 * Every page shares [heightDp] rather than carrying its own, so swiping never moves the
 * favorites underneath — a widget that changed the layout of the rest of the screen as it
 * scrolled past would be far more distracting than the widget itself.
 */
@Composable
fun WidgetSlot(
    widgetIds: List<Int>,
    heightDp: Int,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    allowResize: Boolean = true,
    resizeMode: Boolean = false,
    resizeDragging: Boolean = false,
    moreRequest: Int = 0,
    onStartResize: () -> Unit = {},
    onMenuVisibility: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var menuForId by remember { mutableStateOf(-1) }
    val density = LocalDensity.current

    LaunchedEffect(moreRequest) {
        if (moreRequest > 0 && resizeMode) menuExpanded = true
    }
    LaunchedEffect(menuExpanded) {
        onMenuVisibility(menuExpanded)
    }

    fun openMenu(widgetId: Int, x: Float, y: Float) {
        menuForId = widgetId
        menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
        if (widgetId > 0 && allowResize) onStartResize() else menuExpanded = true
    }

    Box(modifier = modifier.height(heightDp.dp)) {
        if (widgetIds.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { actions.onAddWidget() },
                            onLongPress = { offset -> openMenu(-1, offset.x, offset.y) },
                        )
                    },
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_add), tint = Color.White)
                    Text(stringResource(R.string.widget_add), color = Color.White.copy(alpha = 0.8f))
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { widgetIds.size })
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !resizeMode,
                // Keyed by widget id so adding or removing one doesn't rebuild every host
                // view; neighbors stay alive so a swipe doesn't land on a blank page.
                key = { widgetIds[it] },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val id = widgetIds[page]
                WidgetPage(
                    widgetId = id,
                    sizeUpdatesPaused = resizeDragging,
                    onPressAndHold = { x, y -> openMenu(id, x, y) },
                )
            }

            if (widgetIds.size > 1) {
                // Only worth showing while there is something to say; they fade out rather
                // than sitting permanently on top of somebody's clock.
                var dotsVisible by remember { mutableStateOf(false) }
                LaunchedEffect(pagerState.isScrollInProgress) {
                    if (pagerState.isScrollInProgress) {
                        dotsVisible = true
                    } else {
                        delay(DOTS_LINGER_MS)
                        dotsVisible = false
                    }
                }
                val dotsAlpha by animateFloatAsState(
                    targetValue = if (dotsVisible) 1f else 0f,
                    label = "widgetDots",
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .graphicsLayer { alpha = dotsAlpha },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(widgetIds.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = Color.White.copy(alpha = if (index == pagerState.currentPage) 0.9f else 0.35f),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }

        TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = { menuExpanded = false }) {
            val id = menuForId
            if (id > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAppInfo(id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditLayout() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onWidgetSettings(id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add_another)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_remove_this)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onRemoveWidget(id) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditLayout() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onOpenSettings() },
            )
        }
    }
}

/** Size reporting is bookkeeping, not state that should recompose the widget it measures. */
private class WidgetSizeReporter(private val manager: AppWidgetManager, private val widgetId: Int) {
    var hostView: AppWidgetHostView? = null
    var latestSizeDp = 0 to 0
    private var reportedSizeDp = 0 to 0

    fun reportLatest() {
        val host = hostView ?: return
        val size = latestSizeDp
        val (width, height) = size
        if (width <= 0 || height <= 0 || size == reportedSizeDp) return
        // Preserve the publisher's existing options, including responsive layout metadata.
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull() ?: Bundle()
        host.updateAppWidgetSize(options, width, height, width, height)
        reportedSizeDp = size
    }
}

/** One hosted widget. [onPressAndHold] carries this page's local press position. */
@Composable
private fun WidgetPage(
    widgetId: Int,
    sizeUpdatesPaused: Boolean,
    onPressAndHold: (x: Float, y: Float) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val providerInfo: AppWidgetProviderInfo? = remember(widgetId, appWidgetManager) {
        if (widgetId > 0) appWidgetManager.getAppWidgetInfo(widgetId) else null
    }
    val density = LocalDensity.current

    if (providerInfo == null) {
        // The provider is gone — its app was uninstalled or disabled. Draw something that
        // still takes a long press, or this page is a blank gap with no way to remove it.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(widgetId) {
                    detectTapGestures(onLongPress = { offset -> onPressAndHold(offset.x, offset.y) })
                },
            color = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.widget_unavailable), color = Color.White.copy(alpha = 0.8f))
            }
        }
        return
    }

    val sizes = remember(widgetId, providerInfo, appWidgetManager) {
        WidgetSizeReporter(appWidgetManager, widgetId)
    }
    LaunchedEffect(sizes, sizeUpdatesPaused) {
        if (!sizeUpdatesPaused) {
            // Allow the release/cancellation layout to complete before reporting its size.
            // If layout itself reports first, the remembered reported size makes this a no-op.
            withFrameNanos { }
            sizes.reportLatest()
        }
    }

    // Recreate the host when its identity changes. Ordinary preview recompositions only update
    // the press callback; rebinding the same host every frame repeats framework setup work.
    key(widgetId, providerInfo) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    sizes.latestSizeDp = with(density) {
                        size.width.toDp().value.toInt() to size.height.toDp().value.toInt()
                    }
                    // The real host still measures and draws at every preview size. Only the
                    // provider's binder size notifications wait until the gesture finishes.
                    if (!sizeUpdatesPaused) sizes.reportLatest()
                },
            factory = { ctx ->
                // A broken provider must leave an empty slot, not crash the home screen.
                val hostView = runCatching {
                    app.widgetHost.createView(ctx, widgetId, providerInfo).apply {
                        setAppWidget(widgetId, providerInfo)
                    }
                }.getOrNull()
                sizes.hostView = hostView
                LongPressFrameLayout(ctx).apply {
                    hostView?.let { addView(it) }
                    onLongPress = { x, y -> onPressAndHold(x, y) }
                }
            },
            update = { container ->
                container.onLongPress = { x, y -> onPressAndHold(x, y) }
            },
        )
    }
}
