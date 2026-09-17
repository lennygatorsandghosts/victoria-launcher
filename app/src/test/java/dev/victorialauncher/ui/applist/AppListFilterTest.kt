// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import android.content.ComponentName
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a search does to the sections: the tail two are not alphabetical, so a filtered result
 * has to carry their headings and their strip targets through rather than re-deriving a
 * letter from the heading's first character — which for "Private space" would be P, putting
 * the section back where this whole change took it out of.
 *
 * Only the four kinds whose key is a constant appear here. An AppInfo for an installed app
 * cannot be built on the JVM: its key is its flattened ComponentName and the stub android.jar
 * throws rather than flattening one.
 */
class AppListFilterTest {

    private fun row(kind: EntryKind, label: String) = AppInfo(
        componentName = ComponentName("dev.victorialauncher", "row"),
        label = label,
        kind = kind,
    )

    private val search = row(EntryKind.SEARCH, "Search")
    private val padlock = row(EntryKind.PRIVATE_SPACE, "Unlock private space")
    private val recent = row(EntryKind.RECENT, "Recently installed")
    private val settings = row(EntryKind.SETTINGS, "Vicky+ settings")

    private val model = AppListModel(
        rows = listOf(
            AppListRow.Header("S"),
            AppListRow.Entry(search),
            AppListRow.Header(PRIVATE_SECTION_TITLE, GLYPH_PRIVATE),
            AppListRow.Entry(padlock),
            AppListRow.Header(LAUNCHER_SECTION_TITLE, GLYPH_LAUNCHER),
            AppListRow.Entry(recent),
            AppListRow.Entry(settings),
        ),
        letterIndex = listOf('S' to 0, GLYPH_PRIVATE to 2, GLYPH_LAUNCHER to 4),
    )

    @Test
    fun `the strip's alphabet is the index it was given`() {
        assertEquals(listOf('S', GLYPH_PRIVATE, GLYPH_LAUNCHER), model.letters)
    }

    @Test
    fun `a match inside the private section keeps that section's own heading and glyph`() {
        val filtered = model.filtered { it.kind == EntryKind.PRIVATE_SPACE }
        assertEquals(
            listOf(AppListRow.Header(PRIVATE_SECTION_TITLE, GLYPH_PRIVATE), AppListRow.Entry(padlock)),
            filtered.rows,
        )
        // Not 'P'. Deriving it from the heading's first character is exactly what would file
        // the private space back under P, which is where it used to sit.
        assertEquals(listOf(GLYPH_PRIVATE to 0), filtered.letterIndex)
    }

    @Test
    fun `a match in the launcher section keeps its heading and glyph too`() {
        val filtered = model.filtered { it.kind == EntryKind.RECENT }
        assertEquals(
            listOf(AppListRow.Header(LAUNCHER_SECTION_TITLE, GLYPH_LAUNCHER), AppListRow.Entry(recent)),
            filtered.rows,
        )
        assertEquals(listOf(GLYPH_LAUNCHER to 0), filtered.letterIndex)
    }

    @Test
    fun `an A-Z section still keeps its letter`() {
        val filtered = model.filtered { it.kind == EntryKind.SEARCH }
        assertEquals(listOf(AppListRow.Header("S"), AppListRow.Entry(search)), filtered.rows)
        assertEquals(listOf('S' to 0), filtered.letterIndex)
    }

    @Test
    fun `sections survive in the order they were drawn, with their indices rebuilt`() {
        val filtered = model.filtered { it.kind != EntryKind.SEARCH }
        assertEquals(
            listOf(
                AppListRow.Header(PRIVATE_SECTION_TITLE, GLYPH_PRIVATE),
                AppListRow.Entry(padlock),
                AppListRow.Header(LAUNCHER_SECTION_TITLE, GLYPH_LAUNCHER),
                AppListRow.Entry(recent),
                AppListRow.Entry(settings),
            ),
            filtered.rows,
        )
        assertEquals(listOf(GLYPH_PRIVATE to 0, GLYPH_LAUNCHER to 2), filtered.letterIndex)
    }

    @Test
    fun `a heading whose section matched nothing is dropped, glyph and all`() {
        val filtered = model.filtered { false }
        assertEquals(emptyList<AppListRow>(), filtered.rows)
        assertEquals(emptyList<Pair<Char, Int>>(), filtered.letterIndex)
    }

    @Test
    fun `the launcher's rows carry the keys everything else stores them under`() {
        assertEquals("vicky:recent", recent.key)
        assertEquals("vicky:settings", settings.key)
        // Unchanged, and load-bearing: the padlock row is found by this key everywhere.
        assertEquals("private:space", padlock.key)
    }
}
