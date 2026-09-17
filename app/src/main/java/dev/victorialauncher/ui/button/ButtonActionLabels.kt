// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import dev.victorialauncher.data.ButtonAction

fun actionLabel(action: ButtonAction, resolveEntryLabel: (String) -> String?): String = when (action) {
    ButtonAction.None -> "None"
    is ButtonAction.LaunchEntry -> resolveEntryLabel(action.key) ?: "Unavailable app"
    ButtonAction.WebSearch -> "Web search"
    ButtonAction.SearchApps -> "Search apps"
    ButtonAction.Notifications -> "Notifications"
    ButtonAction.LockScreen -> "Lock screen"
    ButtonAction.LauncherSettings -> "Vicky+ settings"
    ButtonAction.TogglePrivateSpace -> "Lock/unlock private space"
    is ButtonAction.OpenUrl -> action.url
}
