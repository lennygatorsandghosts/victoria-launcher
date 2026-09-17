// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import dev.victorialauncher.data.ButtonAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonActionLabelsTest {
    @Test
    fun `actionLabel names built-in actions`() {
        assertEquals("None", actionLabel(ButtonAction.None) { null })
        assertEquals("Web search", actionLabel(ButtonAction.WebSearch) { null })
        assertEquals("Search apps", actionLabel(ButtonAction.SearchApps) { null })
        assertEquals("Notifications", actionLabel(ButtonAction.Notifications) { null })
        assertEquals("Lock screen", actionLabel(ButtonAction.LockScreen) { null })
        assertEquals("Vicky+ settings", actionLabel(ButtonAction.LauncherSettings) { null })
        assertEquals("Lock/unlock private space", actionLabel(ButtonAction.TogglePrivateSpace) { null })
    }

    @Test
    fun `actionLabel resolves launch entries from the live list`() {
        val action = ButtonAction.LaunchEntry("pkg/Main")
        assertEquals("Calendar", actionLabel(action) { key -> if (key == "pkg/Main") "Calendar" else null })
        assertEquals("Unavailable app", actionLabel(action) { null })
    }

    @Test
    fun `actionLabel shows the url for OpenUrl`() {
        assertEquals("https://example.org", actionLabel(ButtonAction.OpenUrl("https://example.org")) { null })
    }
}
