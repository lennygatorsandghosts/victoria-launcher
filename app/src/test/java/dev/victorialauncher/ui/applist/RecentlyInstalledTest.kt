// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import dev.victorialauncher.data.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyInstalledTest {

    private data class Row(
        val label: String,
        val time: Long,
        val key: String = "pkg/$label",
        val kind: EntryKind = EntryKind.APP,
    )

    private fun recents(rows: List<Row>, hidden: Set<String> = emptySet(), max: Int = 5): List<Row> =
        recentlyInstalledRows(
            rows = rows,
            hidden = hidden,
            max = max,
            kind = { it.kind },
            firstInstallTime = { it.time },
            key = { it.key },
            label = { it.label },
        )

    @Test
    fun `orders newest first and ties by label`() {
        val rows = recents(
            listOf(
                Row("Zulu", 20L),
                Row("Alpha", 20L),
                Row("Beta", 30L),
            )
        )

        assertEquals(listOf("Beta", "Alpha", "Zulu"), rows.map { it.label })
    }

    @Test
    fun `caps result count`() {
        val rows = recents(
            listOf(
                Row("One", 50L),
                Row("Two", 40L),
                Row("Three", 30L),
            ),
            max = 2,
        )

        assertEquals(listOf("One", "Two"), rows.map { it.label })
    }

    @Test
    fun `excludes hidden rows`() {
        val rows = recents(
            listOf(
                Row("Visible", 20L, key = "pkg/Visible"),
                Row("Hidden", 30L, key = "pkg/Hidden"),
            ),
            hidden = setOf("pkg/Hidden"),
        )

        assertEquals(listOf("Visible"), rows.map { it.label })
    }

    @Test
    fun `excludes zero time rows from other profiles`() {
        val rows = recents(
            listOf(
                Row("Main", 20L),
                Row("Work", 0L, key = "pkg/Work|u10"),
            )
        )

        assertEquals(listOf("Main"), rows.map { it.label })
    }

    @Test
    fun `excludes non app kinds`() {
        val rows = recents(
            listOf(
                Row("App", 20L),
                Row("Shortcut", 30L, kind = EntryKind.SHORTCUT),
                Row("Recent", 40L, kind = EntryKind.RECENT),
            )
        )

        assertEquals(listOf("App"), rows.map { it.label })
    }
}
