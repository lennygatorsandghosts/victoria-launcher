// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box tests for the "Recently installed" suggestions shown in the app list's search field
 * (DESIGN2 §6, dev.victorialauncher.ui.applist.recentlyInstalled), which appear only while that
 * field is focused and empty.
 *
 * The home header is turned off for the duration: it sits behind the app list overlay and, on
 * some layouts, its date text can remain in the accessibility tree even while visually covered,
 * which would confuse a plain "how many extra text rows are there" count.
 */
@RunWith(AndroidJUnit4::class)
class RecentSuggestionsTest {

    private val device: UiDevice get() = LauncherTestUtils.uiDevice()
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        prefs = Prefs(context)
        runBlocking { prefs.setHomeHeaderEnabled(false) }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()

        // Whichever test method runs first pays for a real, uncancellable first composition of
        // a freshly (re)installed process (see VickyButtonTest's setUp comment for the full
        // reasoning) — on a loaded/shared emulator host that can badly outrun a normal
        // UI-interaction timeout. Paying for it once here keeps that cold-boot cost off
        // whichever test happens to run first.
        check(device.wait(Until.hasObject(By.desc(BUTTON_DESC)), READY_WAIT_MS)) {
            "the launcher's home screen never composed after going home"
        }
    }

    @After
    fun tearDown() {
        runBlocking { prefs.setHomeHeaderEnabled(true) }
    }

    private fun focusSearchField() {
        LauncherTestUtils.openAppList()
        val field = device.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS)), 5_000L)
            ?: error("the app list's search box never appeared")
        field.click()
        assertTrue(
            "expected the search field to be focused after tapping it",
            device.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS).focused(true)), 5_000L) != null,
        )
    }

    @Test
    fun focusedEmptySearchShowsRecentlyInstalledCaptionWithAtLeastOneRow() {
        focusSearchField()

        assertTrue(
            "expected the Recently installed caption once the empty field is focused",
            LauncherTestUtils.waitForText(RECENT_CAPTION),
        )

        // Every visible text on screen besides the caption itself and the field's own
        // placeholder is a suggested row — there is nothing else the search overlay draws
        // while showing recents.
        val rowLabels = device.findObjects(By.clazz(TEXT_VIEW_CLASS))
            .mapNotNull { it.text }
            .filterNot { it == RECENT_CAPTION || it == SEARCH_PLACEHOLDER }
        assertTrue(
            "expected at least one recently-installed row under the caption, found: $rowLabels",
            rowLabels.isNotEmpty(),
        )
    }

    @Test
    fun typingAQueryReplacesTheRecentCaption() {
        focusSearchField()
        assertTrue(LauncherTestUtils.waitForText(RECENT_CAPTION))

        LauncherTestUtils.filterAppList("Sett")

        assertTrue(
            "expected the Recently installed caption to disappear once a query is typed",
            device.wait(Until.gone(By.text(RECENT_CAPTION)), 5_000L),
        )
        assertTrue(
            "expected the ordinary search results to show instead",
            LauncherTestUtils.waitForText("Settings"),
        )
    }

    private companion object {
        const val RECENT_CAPTION = "Recently installed"
        const val SEARCH_PLACEHOLDER = "Search apps"
        const val EDIT_TEXT_CLASS = "android.widget.EditText"
        const val TEXT_VIEW_CLASS = "android.widget.TextView"
        const val BUTTON_DESC = "Vicky+ button"

        /** One-time cold-boot budget for [setUp]; see the comment there. */
        const val READY_WAIT_MS = 120_000L
    }
}
