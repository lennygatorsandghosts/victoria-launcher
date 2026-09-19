// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutCandidatesTest {

    private data class FakeShortcut(val appLabel: String, val label: String, val id: String)

    @Test
    fun `candidates are grouped by app label and sorted within each app`() {
        val candidates = listOf(
            FakeShortcut("Beta", "Second", "b2"),
            FakeShortcut("Alpha", "Maps", "a2"),
            FakeShortcut("Beta", "First", "b1"),
            FakeShortcut("Alpha", "Calendar", "a1"),
        )

        val groups = groupShortcutCandidates(candidates, FakeShortcut::appLabel, FakeShortcut::label)

        assertEquals(listOf("Alpha", "Beta"), groups.map { it.appLabel })
        assertEquals(listOf("Calendar", "Maps"), groups[0].shortcuts.map { it.label })
        assertEquals(listOf("First", "Second"), groups[1].shortcuts.map { it.label })
    }
}
