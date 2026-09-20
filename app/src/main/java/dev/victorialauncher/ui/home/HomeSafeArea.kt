// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

/** Shared pixel geometry for home placement and widget resizing. Inputs are live insets. */
data class HomeSafeArea(
    val windowWidth: Float,
    val windowHeight: Float,
    val statusTop: Float = 0f,
    val cutoutTop: Float = 0f,
    val navigationBottom: Float = 0f,
    val gestureBottom: Float = 0f,
    val insetLeft: Float = 0f,
    val insetRight: Float = 0f,
    val enabled: Boolean = true,
    val margin: Float = 0f,
) {
    private val extra get() = margin.coerceAtLeast(0f)
    val left: Float get() = if (enabled) (insetLeft.coerceAtLeast(0f) + extra)
        .coerceIn(0f, windowWidth.coerceAtLeast(0f)) else 0f
    val top: Float get() = if (enabled) (maxOf(0f, statusTop, cutoutTop) + extra)
        .coerceIn(0f, windowHeight.coerceAtLeast(0f)) else 0f
    val right: Float get() = if (enabled) (windowWidth - insetRight.coerceAtLeast(0f) - extra)
        .coerceIn(left, windowWidth.coerceAtLeast(left)) else windowWidth.coerceAtLeast(0f)
    val bottom: Float get() = if (enabled) (windowHeight - maxOf(0f, navigationBottom, gestureBottom) - extra)
        .coerceIn(top, windowHeight.coerceAtLeast(top)) else windowHeight.coerceAtLeast(0f)
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun minOffset(contentHeight: Float): Float = minOf(0f, height - contentHeight)
    /** The optional content bounds also respect the capped stack width on large screens. */
    fun clampOffsetX(
        requested: Float,
        widgetLeft: Float,
        widgetWidth: Float,
        contentLeft: Float = left,
        contentRight: Float = right,
    ): Float {
        if (!enabled) return requested
        val minimum = maxOf(left, contentLeft) - widgetLeft
        val maximum = minOf(right, contentRight) - widgetLeft - widgetWidth.coerceAtLeast(0f)
        return requested.coerceIn(minimum, maximum.coerceAtLeast(minimum))
    }
}
