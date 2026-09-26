// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How much of a shortcut's icon the badge of the app it opens in takes up. Big enough to tell
 * one app from another at the smallest icon size offered, small enough that the shortcut's
 * own picture is still the thing you see first; about what Launcher3 and Niagara draw.
 */
const val BADGE_RATIO = 0.40f

/** The gap cut around the badge, so it reads against whatever the icon has in that corner. */
const val RING_RATIO = 0.03f

/** Below this a badge is a smudge rather than an icon, and none is drawn at all. */
const val MIN_BADGE_PX = 8

/**
 * Where the badge goes in a square icon: [left], [top] and [size] are the badge itself, flush
 * with the bottom and end corner, and the halo is the badge grown by [ring] on every side,
 * which is what gets cut out of the icon first. The halo runs off the icon's own edges on
 * those two sides; only the part inside it is ever drawn.
 */
data class BadgeGeometry(
    val left: Int,
    val top: Int,
    val size: Int,
    val ring: Int,
    val haloLeft: Int,
    val haloTop: Int,
    val haloSize: Int,
)

/**
 * The badge for an icon [px] pixels square, or null when it would be too small to be worth
 * drawing. Kept apart from the drawing so every size can be checked without a device.
 */
fun badgeGeometry(px: Int, ratio: Float = BADGE_RATIO, ring: Float = RING_RATIO): BadgeGeometry? {
    require(ratio > 0f && ratio < 1f) { "ratio must be greater than 0 and less than 1" }
    val size = (px * ratio).roundToInt()
    if (size < MIN_BADGE_PX) return null

    // Never less than a pixel: at small sizes a ring that rounds away leaves the badge
    // touching the picture under it, which is the thing the ring is there to stop.
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

/**
 * Only a shortcut with a picture of its own is badged. One without is drawn with the icon of
 * the app it opens in already, and a second, smaller copy of that in the corner says nothing.
 */
fun badgePlan(isShortcut: Boolean, enabled: Boolean, hasPublisher: Boolean, hasOwnIcon: Boolean): BadgePlan =
    if (isShortcut && enabled && hasPublisher && hasOwnIcon) BadgePlan.BADGE else BadgePlan.NONE

/**
 * What a badged shortcut adds to its icon's cache key. Empty for everything else, so an app's
 * icon, and a shortcut's with the setting off, are cached under exactly the key they always
 * were. [overridesStamp] stands for every custom icon set, since one set on the app a shortcut
 * opens in changes its badge.
 */
fun badgeKeySuffix(isShortcut: Boolean, enabled: Boolean, overridesStamp: Int): String =
    if (isShortcut && enabled) "|b1|$overridesStamp" else ""
