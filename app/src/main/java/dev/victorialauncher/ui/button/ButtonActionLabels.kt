// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R
import dev.victorialauncher.data.ButtonAction

@StringRes
internal fun actionLabelResource(action: ButtonAction): Int? = when (action) {
    ButtonAction.None -> R.string.button_action_none
    is ButtonAction.LaunchEntry -> null
    ButtonAction.WebSearch -> R.string.button_action_web_search
    ButtonAction.SearchApps -> R.string.button_action_search_apps
    ButtonAction.Notifications -> R.string.button_action_notifications
    ButtonAction.LockScreen -> R.string.button_action_lock_screen
    ButtonAction.LauncherSettings -> R.string.settings_title
    is ButtonAction.OpenUrl -> null
}

@Composable
fun actionLabel(action: ButtonAction, resolveEntryLabel: (String) -> String?): String = when (action) {
    is ButtonAction.LaunchEntry -> resolveEntryLabel(action.key) ?: stringResource(R.string.button_action_unavailable_app)
    is ButtonAction.OpenUrl -> action.url
    else -> stringResource(actionLabelResource(action) ?: R.string.button_action_none)
}

internal fun actionLabelForTest(
    action: ButtonAction,
    resolveEntryLabel: (String) -> String?,
    resolveString: (Int) -> String,
): String = when (action) {
    is ButtonAction.LaunchEntry -> resolveEntryLabel(action.key) ?: resolveString(R.string.button_action_unavailable_app)
    is ButtonAction.OpenUrl -> action.url
    else -> resolveString(actionLabelResource(action) ?: R.string.button_action_none)
}
