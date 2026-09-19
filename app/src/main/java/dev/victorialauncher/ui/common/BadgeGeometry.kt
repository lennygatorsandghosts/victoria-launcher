package dev.victorialauncher.ui.common

import kotlin.math.max
import kotlin.math.roundToInt

const val BADGE_RATIO = 0.40f
const val RING_RATIO = 0.03f
const val MIN_BADGE_PX = 8

data class BadgeGeometry(
    val left: Int,
    val top: Int,
    val size: Int,
    val ring: Int,
    val haloLeft: Int,
    val haloTop: Int,
    val haloSize: Int,
)

fun badgeGeometry(px: Int, ratio: Float = BADGE_RATIO, ring: Float = RING_RATIO): BadgeGeometry? {
    require(ratio > 0f && ratio < 1f) { "ratio must be greater than 0 and less than 1" }
    val size = (px * ratio).roundToInt()
    if (size < MIN_BADGE_PX) return null

    val ringPx = max(1, (px * ring).roundToInt())
    val left = px - size
    val top = px - size
    return BadgeGeometry(
        left = left,
        top = top,
        size = size,
        ring = ringPx,
        haloLeft = left - ringPx,
        haloTop = top - ringPx,
        haloSize = size + ringPx * 2,
    )
}

enum class BadgePlan { NONE, BADGE }

fun badgePlan(isShortcut: Boolean, enabled: Boolean, hasPublisher: Boolean, hasOwnIcon: Boolean): BadgePlan =
    if (isShortcut && enabled && hasPublisher && hasOwnIcon) BadgePlan.BADGE else BadgePlan.NONE

fun badgeKeySuffix(isShortcut: Boolean, enabled: Boolean, overridesStamp: Int): String =
    if (isShortcut && enabled) "|b1|$overridesStamp" else ""
