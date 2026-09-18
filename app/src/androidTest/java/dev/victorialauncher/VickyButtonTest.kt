// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Instrumentation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.data.SearchUrl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box tests for the Vicky+ button (DESIGN2 §3): a round FAB, found by its content
 * description ([BUTTON_DESC], set in [dev.victorialauncher.ui.button.VickyButton]), whose tap
 * behavior depends on whether the app list is already open and whether a search template is
 * configured — see [dev.victorialauncher.data.effectiveActions].
 */
@RunWith(AndroidJUnit4::class)
class VickyButtonTest {

    private lateinit var instrumentation: Instrumentation
    private lateinit var device: UiDevice
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        device = LauncherTestUtils.uiDevice()
        // Same underlying DataStore the running app reads — see SearchEntryTest's doc comment.
        prefs = Prefs(instrumentation.targetContext.applicationContext)
        // A clean baseline: no search template, so the default tap action is SearchApps
        // rather than whatever an earlier test left configured.
        runBlocking { prefs.setSearchUrlTemplate("") }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()

        // The process is only ever cold once per class run — whichever test method the
        // runner happens to execute first pays for a real, uncancellable first composition
        // (DataStore's first read plus the full queryAllApps() scan behind it; see
        // SearchSettingsSaveOnLeaveTest's CR-3 doc comment) on top of the button's own first
        // render. On a loaded/shared machine that can badly outrun a normal UI-interaction
        // timeout. Paying for it once here, before any test's own timing-sensitive assertions
        // start, keeps that cold-boot cost from being charged unevenly to whichever test
        // happens to run first — every method starts from the same warm baseline.
        check(device.wait(Until.hasObject(By.desc(BUTTON_DESC)), READY_WAIT_MS)) {
            "the Vicky+ button never appeared after going home"
        }
    }

    @After
    fun tearDown() {
        runBlocking { prefs.setSearchUrlTemplate("") }
    }

    private fun theButton() =
        device.wait(Until.findObject(By.desc(BUTTON_DESC)), WAIT_MS)
            ?: error("the Vicky+ button never appeared")

    private fun focusedEditTextShown(): Boolean =
        device.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS).focused(true)), WAIT_MS) != null

    @Test
    fun theButtonIsOnTheHomeScreen() {
        assertTrue(
            "expected the Vicky+ button on the home screen",
            device.wait(Until.hasObject(By.desc(BUTTON_DESC)), WAIT_MS),
        )
    }

    @Test
    fun tappingWithNoTemplateOpensTheAppListWithSearchFocused() {
        theButton().click()

        assertTrue(
            "expected the A-Z list to open (tap with no template defaults to SearchApps)",
            LauncherTestUtils.waitForText("Search apps"),
        )
        assertTrue("expected the search field to be focused", focusedEditTextShown())
    }

    @Test
    fun withTheAppListOpenTheButtonShowsAndTappingFocusesSearch() {
        LauncherTestUtils.openAppList()
        assertTrue(
            "expected the Vicky+ button to still show with the list open",
            device.wait(Until.hasObject(By.desc(BUTTON_DESC)), WAIT_MS),
        )
        // The edge-gesture open does not itself focus the field, so tapping the button below
        // is what has to be shown to make the difference.
        assertFalse(
            "expected the search field not to be focused yet, or the tap below proves nothing",
            device.hasObject(By.clazz(EDIT_TEXT_CLASS).focused(true)),
        )

        theButton().click()

        assertTrue(
            "expected tapping the button to focus the app list's search field",
            focusedEditTextShown(),
        )
    }

    @Test
    fun longPressOpensTheEditSheetWithFourActionRows() {
        theButton().longClick()

        assertTrue(LauncherTestUtils.waitForText("Vicky+ button"))
        assertTrue(LauncherTestUtils.waitForText("Edit tap action"))
        assertTrue(LauncherTestUtils.waitForText("Edit swipe up action"))
        assertTrue(LauncherTestUtils.waitForText("Edit swipe left action"))
        assertTrue(LauncherTestUtils.waitForText("Edit swipe right action"))

        // Leave the sheet closed for whatever runs next.
        device.pressBack()
    }

    @Test
    fun settingATemplateThenTappingAndSearchingFiresTheBuiltUrl() {
        val template = "https://example.org/search?q=%s"
        runBlocking { prefs.setSearchUrlTemplate(template) }

        val query = "cats and dogs"
        val expectedUrl = SearchUrl.build(template, query)
        assertNotNull("the fixture template/query should themselves build a URL", expectedUrl)
        assertEquals("https://example.org/search?q=cats%20and%20dogs", expectedUrl)

        val monitor = CapturingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            LauncherTestUtils.goHome()
            theButton().click()

            assertTrue(
                "expected the bottom search bar to appear",
                LauncherTestUtils.waitForText("Search the web", 5_000L),
            )
            val field = device.findObject(By.clazz(EDIT_TEXT_CLASS))
                ?: error("expected the search bar's text field")
            field.text = query
            device.pressEnter()

            val deadline = System.currentTimeMillis() + WAIT_MS
            while (monitor.captured == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }

            val firedIntent = monitor.captured
            assertNotNull("expected the search bar to have started a VIEW activity", firedIntent)
            assertEquals(Intent.ACTION_VIEW, firedIntent!!.action)
            assertEquals(expectedUrl, firedIntent.dataString)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** See SearchEntryTest's doc comment for why this intercepts rather than really launching. */
    private class CapturingMonitor : Instrumentation.ActivityMonitor() {
        @Volatile var captured: Intent? = null

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW) return null
            if (intent.data?.scheme?.lowercase() !in SCHEMES) return null
            captured = intent
            return Instrumentation.ActivityResult(0, null)
        }

        private companion object {
            val SCHEMES = setOf("http", "https")
        }
    }

    private companion object {
        const val BUTTON_DESC = "Vicky+ button"
        const val EDIT_TEXT_CLASS = "android.widget.EditText"

        /** Ordinary post-warm-up UI wait, once the process is known to be up and composed. */
        const val WAIT_MS = 10_000L

        /**
         * Budget for the one-time cold-boot check in [setUp]. Generous on purpose: this is
         * paid at most once per class run (see the comment there), not per test, and a shared,
         * memory-pressured emulator host can make a truly cold Compose-plus-DataStore first
         * render take far longer than any single UI interaction should ever need.
         */
        const val READY_WAIT_MS = 120_000L
    }
}
