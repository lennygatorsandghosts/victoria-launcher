package dev.victorialauncher.ui.common

data class BadgeGeometry(
    val left: Int,
    val top: Int,
    val size: Int,
    val ring: Int,
    val haloLeft: Int,
    val haloTop: Int,
    val haloSize: Int,
)

fun badgeGeometry(px: Int, ratio: Float = 0.40f, ringRatio: Float = 0.03f): BadgeGeometry? =
    TODO("not implemented")

fun badgeKeySuffix(isShortcut: Boolean, enabled: Boolean, overridesStamp: Int): String =
    TODO("not implemented")
