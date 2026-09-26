// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

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

// Not written yet: the tests in BadgeGeometryTest say what these must do.
fun badgeGeometry(px: Int, ratio: Float = 0.40f, ring: Float = 0.03f): BadgeGeometry? =
    TODO("not implemented")

fun badgeKeySuffix(isShortcut: Boolean, enabled: Boolean, overridesStamp: Int): String =
    TODO("not implemented")
