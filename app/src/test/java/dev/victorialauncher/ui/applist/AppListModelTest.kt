// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a row ends up, which is the whole of round 2 item 1: the private space gets a section
 * of its own at the bottom instead of being filed under P and C and S like everything else,
 * and the launcher's own rows get one below that.
 *
 * Tested through [buildSectionedRows] rather than through `buildAppListModel`, because an
 * AppInfo cannot be built on the JVM at all — its key is its flattened ComponentName, and the
 * stub android.jar throws instead of flattening one. Everything that decides the shape of the
 * list lives in the generic function; `buildAppListModel` only says which of these questions
 * an AppInfo answers.
 */
class AppListModelTest {

    /**
     * Stands in for an AppInfo. [serial] is the profile's, which is the only thing that says
     * a row is private — never the key's `|u` suffix, which a work profile has too.
     */
    private data class Row(
        val name: String,
        val serial: Long = 0L,
        val padlock: Boolean = false,
        val launcher: Int? = null,
        val english: String? = null,
    )

    private companion object {
        /** A private space's serial, and a work profile's. Neither is ever zero. */
        const val PRIVATE = 11L
        const val WORK = 10L
    }

    /** The rows as the list draws them: "A", "Chrome", … so an assertion reads like a screen. */
    private fun build(
        rows: List<Row>,
        hidden: Set<String> = emptySet(),
        launchCounts: Map<String, Int> = emptyMap(),
        privateSerial: Long = PRIVATE,
    ): Pair<List<String>, List<Pair<Char, Int>>> = buildSectionedRows(
        items = rows,
        key = { it.name },
        hidden = hidden,
        displayName = { it.name },
        englishName = { it.english },
        launchCounts = launchCounts,
        isPrivate = { it.padlock || (privateSerial != 0L && it.serial == privateSerial) },
        isPadlock = { it.padlock },
        launcherRank = { it.launcher },
        privateTitle = PRIVATE_SECTION_TITLE,
        launcherTitle = LAUNCHER_SECTION_TITLE,
        header = { text, _ -> text },
        entry = { it.name },
    )

    private val padlock = Row("Unlock private space", padlock = true)
    private val recent = Row("Recently installed", launcher = 0)
    private val settingsRow = Row("Vicky+ settings", launcher = 1)

    @Test
    fun `a work profile's apps stay in A-Z and the private space's do not`() {
        val (rows, _) = build(
            listOf(
                Row("Chrome"),
                Row("Clock", serial = PRIVATE),
                Row("Calendar", serial = WORK),
                padlock,
            )
        )
        assertEquals(
            listOf(
                "C", "Calendar", "Chrome",
                PRIVATE_SECTION_TITLE, "Unlock private space", "Clock",
            ),
            rows,
        )
    }

    @Test
    fun `the sections come in one order and the launcher's is last`() {
        val (rows, _) = build(
            listOf(settingsRow, Row("Zoom"), recent, Row("Clock", serial = PRIVATE), padlock, Row("Alarm"))
        )
        assertEquals(
            listOf(
                "A", "Alarm",
                "Z", "Zoom",
                PRIVATE_SECTION_TITLE, "Unlock private space", "Clock",
                LAUNCHER_SECTION_TITLE, "Recently installed", "Vicky+ settings",
            ),
            rows,
        )
    }

    @Test
    fun `the padlock is the first row of its section however the apps sort`() {
        // A name that sorts before the padlock's own, so an alphabetical section would put it
        // first and this would pass by accident.
        val (rows, _) = build(listOf(Row("Aardvark", serial = PRIVATE), padlock))
        assertEquals(
            listOf(PRIVATE_SECTION_TITLE, "Unlock private space", "Aardvark"),
            rows,
        )
    }

    @Test
    fun `the private space's own apps are alphabetical, not by launch count`() {
        // The order launch counts would impose is exactly the reverse, so this can only pass
        // if the counts were ignored: how often something in there was opened is not an order
        // to put on screen.
        val (rows, _) = build(
            rows = listOf(padlock, Row("Signal", serial = PRIVATE), Row("Banking", serial = PRIVATE)),
            launchCounts = mapOf("Signal" to 99),
        )
        assertEquals(
            listOf(PRIVATE_SECTION_TITLE, "Unlock private space", "Banking", "Signal"),
            rows,
        )
    }

    @Test
    fun `launch counts still order the A-Z sections`() {
        val (rows, _) = build(
            rows = listOf(Row("Alarm"), Row("Aardvark")),
            launchCounts = mapOf("Alarm" to 5),
        )
        assertEquals(listOf("A", "Alarm", "Aardvark"), rows)
    }

    @Test
    fun `the strip targets every section, in the order they are drawn`() {
        val (rows, letterIndex) = build(
            listOf(Row("Alarm"), Row("Zoom"), padlock, Row("Clock", serial = PRIVATE), recent, settingsRow)
        )
        assertEquals(
            listOf('A' to 0, 'Z' to 2, GLYPH_PRIVATE to 4, GLYPH_LAUNCHER to 7),
            letterIndex,
        )
        // Each index is the section's own header, which is what scrubbing to it scrolls to.
        letterIndex.forEach { (_, index) ->
            assertTrue("index $index is past the end", index in rows.indices)
        }
        assertEquals("A", rows[0])
        assertEquals("Z", rows[2])
        assertEquals(PRIVATE_SECTION_TITLE, rows[4])
        assertEquals(LAUNCHER_SECTION_TITLE, rows[7])
    }

    @Test
    fun `no private space means no section and no glyph`() {
        val (rows, letterIndex) = build(
            rows = listOf(Row("Alarm"), recent, settingsRow),
            privateSerial = 0L,
        )
        assertEquals(
            listOf("A", "Alarm", LAUNCHER_SECTION_TITLE, "Recently installed", "Vicky+ settings"),
            rows,
        )
        assertEquals(listOf('A' to 0, GLYPH_LAUNCHER to 2), letterIndex)
    }

    @Test
    fun `a locked space is a header and a padlock and nothing else`() {
        // Locked is the listing already in place: not one private app row is enumerated, so
        // the section can only ever be these two rows. Nothing here counts or names anything
        // inside the space, because there is nothing here that has ever seen it.
        val (rows, letterIndex) = build(listOf(Row("Alarm"), padlock))
        assertEquals(listOf("A", "Alarm", PRIVATE_SECTION_TITLE, "Unlock private space"), rows)
        assertEquals(listOf('A' to 0, GLYPH_PRIVATE to 2), letterIndex)
    }

    @Test
    fun `hiding a row takes it out of whichever section it was in`() {
        val (rows, letterIndex) = build(
            rows = listOf(Row("Alarm"), padlock, Row("Clock", serial = PRIVATE), recent, settingsRow),
            hidden = setOf("Clock", "Recently installed"),
        )
        assertEquals(
            listOf(
                "A", "Alarm",
                PRIVATE_SECTION_TITLE, "Unlock private space",
                LAUNCHER_SECTION_TITLE, "Vicky+ settings",
            ),
            rows,
        )
        assertEquals(listOf('A' to 0, GLYPH_PRIVATE to 2, GLYPH_LAUNCHER to 4), letterIndex)
    }

    @Test
    fun `a name with no letter of its own still files under its English one`() {
        // Unchanged behavior, asserted here because the partition now runs before the
        // grouping does and the fallback had to be carried through it.
        val (rows, _) = build(listOf(Row("ブルー", english = "Blue")))
        assertEquals(listOf("B", "ブルー"), rows)
    }

    @Test
    fun `a private row whose name has no A-Z letter still lands in the section`() {
        val (rows, letterIndex) = build(listOf(padlock, Row("設定", serial = PRIVATE)))
        assertEquals(listOf(PRIVATE_SECTION_TITLE, "Unlock private space", "設定"), rows)
        assertEquals(listOf(GLYPH_PRIVATE to 0), letterIndex)
    }

    @Test
    fun `an A-Z header takes its own letter as its strip target`() {
        assertEquals('C', AppListRow.Header("C").indexChar)
        assertEquals(GLYPH_PRIVATE, AppListRow.Header(PRIVATE_SECTION_TITLE, GLYPH_PRIVATE).indexChar)
        assertEquals(GLYPH_LAUNCHER, AppListRow.Header(LAUNCHER_SECTION_TITLE, GLYPH_LAUNCHER).indexChar)
    }
}
