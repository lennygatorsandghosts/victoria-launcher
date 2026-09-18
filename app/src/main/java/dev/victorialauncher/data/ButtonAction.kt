// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import java.net.URI
import java.net.URISyntaxException

internal const val MAX_URL_LENGTH = 512

sealed interface ButtonAction {
    fun encode(): String = when (this) {
        None -> "none"
        is LaunchEntry -> "entry:$key"
        WebSearch -> "web"
        SearchApps -> "apps"
        Notifications -> "notif"
        LockScreen -> "lock"
        LauncherSettings -> "settings"
        TogglePrivateSpace -> "private"
        is OpenUrl -> "url:$url"
    }

    object None : ButtonAction
    data class LaunchEntry(val key: String) : ButtonAction
    object WebSearch : ButtonAction
    object SearchApps : ButtonAction
    object Notifications : ButtonAction
    object LockScreen : ButtonAction
    object LauncherSettings : ButtonAction
    object TogglePrivateSpace : ButtonAction
    data class OpenUrl(val url: String) : ButtonAction

    companion object {
        private const val ENTRY_PREFIX = "entry:"
        private const val URL_PREFIX = "url:"

        fun parse(value: String?): ButtonAction? {
            val raw = value?.trim() ?: return null
            return when {
                raw == "none" -> None
                raw.startsWith(ENTRY_PREFIX) -> {
                    val key = raw.removePrefix(ENTRY_PREFIX)
                    if (key.isBlank()) null else LaunchEntry(key)
                }
                raw == "web" -> WebSearch
                raw == "apps" -> SearchApps
                raw == "notif" -> Notifications
                raw == "lock" -> LockScreen
                raw == "settings" -> LauncherSettings
                raw == "private" -> TogglePrivateSpace
                raw.startsWith(URL_PREFIX) -> {
                    val url = raw.removePrefix(URL_PREFIX).trim()
                    if (isAllowedOpenUrl(url)) OpenUrl(url) else null
                }
                else -> null
            }
        }

        private fun isAllowedOpenUrl(url: String): Boolean {
            if (url.isEmpty() || url.length > MAX_URL_LENGTH) return false
            if (url.any { it.isIsoControl() || it == '\\' }) return false

            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                return false
            }

            val scheme = uri.scheme ?: return false
            if (!scheme.equals("https", ignoreCase = true) && !scheme.equals("http", ignoreCase = true)) {
                return false
            }
            if (!uri.isAbsolute) return false

            val rawAuthority = uri.rawAuthority
            if (rawAuthority.isNullOrEmpty()) return false
            if (rawAuthority.contains('@')) return false
            if (rawAuthority.contains('%')) return false

            val host = if (rawAuthority.startsWith("[")) {
                val closingBracket = rawAuthority.indexOf(']')
                if (closingBracket < 0) rawAuthority else rawAuthority.substring(0, closingBracket + 1)
            } else {
                rawAuthority.substringBeforeLast(':')
            }
            if (host.isEmpty()) return false

            val prefix = "$scheme://"
            return url.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)
        }

        private fun Char.isIsoControl(): Boolean = Character.isISOControl(this)
    }
}

enum class ButtonSlot { TAP, SWIPE_UP, SWIPE_LEFT, SWIPE_RIGHT }

fun effectiveActions(
    stored: Map<ButtonSlot, String?>,
    hasSearchTemplate: Boolean,
    quickLeftKey: String?,
    quickRightKey: String?,
): Map<ButtonSlot, ButtonAction> = mapOf(
    ButtonSlot.TAP to (
        ButtonAction.parse(stored[ButtonSlot.TAP])
            ?: if (hasSearchTemplate) ButtonAction.WebSearch else ButtonAction.SearchApps
        ),
    ButtonSlot.SWIPE_UP to (
        ButtonAction.parse(stored[ButtonSlot.SWIPE_UP])
            ?: ButtonAction.SearchApps
        ),
    ButtonSlot.SWIPE_LEFT to (
        ButtonAction.parse(stored[ButtonSlot.SWIPE_LEFT])
            ?: quickLeftKey?.takeIf { it.isNotBlank() }?.let(ButtonAction::LaunchEntry)
            ?: ButtonAction.None
        ),
    ButtonSlot.SWIPE_RIGHT to (
        ButtonAction.parse(stored[ButtonSlot.SWIPE_RIGHT])
            ?: quickRightKey?.takeIf { it.isNotBlank() }?.let(ButtonAction::LaunchEntry)
            ?: ButtonAction.None
        ),
)
