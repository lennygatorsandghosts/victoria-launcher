// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import dev.victorialauncher.R
import dev.victorialauncher.data.ButtonAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonActionLabelsTest {
    private val strings = mapOf(
        R.string.button_action_none to "None",
        R.string.button_action_unavailable_app to "Unavailable app",
        R.string.button_action_web_search to "Web search",
        R.string.button_action_search_apps to "Search apps",
        R.string.button_action_notifications to "Notifications",
        R.string.button_action_lock_screen to "Lock screen",
        R.string.settings_title to "Victoria settings",
    )

    private fun stringFor(id: Int): String = strings.getValue(id)

    @Test
    fun `actionLabel names built-in actions`() {
        assertEquals("None", actionLabelForTest(ButtonAction.None, { null }, ::stringFor))
        assertEquals("Web search", actionLabelForTest(ButtonAction.WebSearch, { null }, ::stringFor))
        assertEquals("Search apps", actionLabelForTest(ButtonAction.SearchApps, { null }, ::stringFor))
        assertEquals("Notifications", actionLabelForTest(ButtonAction.Notifications, { null }, ::stringFor))
        assertEquals("Lock screen", actionLabelForTest(ButtonAction.LockScreen, { null }, ::stringFor))
        assertEquals("Victoria settings", actionLabelForTest(ButtonAction.LauncherSettings, { null }, ::stringFor))
    }

    @Test
    fun `actionLabel resolves launch entries from the live list`() {
        val action = ButtonAction.LaunchEntry("pkg/Main")
        assertEquals("Calendar", actionLabelForTest(action, { key -> if (key == "pkg/Main") "Calendar" else null }, ::stringFor))
        assertEquals("Unavailable app", actionLabelForTest(action, { null }, ::stringFor))
    }

    @Test
    fun `actionLabel shows the url for OpenUrl`() {
        assertEquals("https://example.org", actionLabelForTest(ButtonAction.OpenUrl("https://example.org"), { null }, ::stringFor))
    }
}
