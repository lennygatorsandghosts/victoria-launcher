// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for CR-3: the Settings "Search button" fields debounce their write to
 * Prefs (each write drives a full, uncancellable `queryAllApps()` via VictoriaNavHost's
 * `LaunchedEffect(searchUrlTemplate, searchLabel)`) rather than saving on every keystroke.
 *
 * A UI-driven test can't observe the debounce's write COUNT without reaching into Prefs
 * mid-typing (that side is checked separately, by hand, with a temporary logcat count — see
 * the CR-3 commit message). What it can check end to end is the other half of the fix: text
 * entered right before leaving the screen must not be lost to a debounce that never got to
 * fire. Uses the Label field rather than the URL field — both fields run the identical
 * local-state/debounce/flush-on-dispose code, and Label is the first (and, once scrolled to,
 * only) `EditText` in the Search button section, so it's reachable without disambiguating
 * between the two fields.
 */
@RunWith(AndroidJUnit4::class)
class SearchSettingsSaveOnLeaveTest {

    private lateinit var instrumentation: Instrumentation
    private lateinit var device: UiDevice
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        device = LauncherTestUtils.uiDevice()
        // Same underlying DataStore the running app reads — see SearchEntryTest's doc comment.
        prefs = Prefs(instrumentation.targetContext.applicationContext)
        runBlocking {
            prefs.setSearchUrlTemplate("")
            prefs.setSearchLabel("")
        }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    @After
    fun tearDown() {
        runBlocking {
            prefs.setSearchUrlTemplate("")
            prefs.setSearchLabel("")
        }
    }

    @Test
    fun textTypedRightBeforeBackIsStillSaved() {
        val settingsLabel = instrumentation.targetContext.getString(R.string.action_open_settings)
        val urlFieldLabel = instrumentation.targetContext.getString(R.string.settings_search_button_url)

        LauncherTestUtils.openAppList()
        // A query nothing installed matches leaves only the pinned Settings row on screen —
        // reachable without scrolling however many apps happen to be on this device.
        LauncherTestUtils.filterAppList("zzz-no-such-app-zzz")
        assertTrue("expected the Settings row", LauncherTestUtils.waitForText(settingsLabel))
        device.findObject(By.text(settingsLabel)).click()

        assertTrue(
            "expected the Settings screen, with the Search button section reachable",
            scrollDownUntilVisible(urlFieldLabel),
        )

        val labelField = device.findObject(By.clazz("android.widget.EditText"))
            ?: error("expected the Search button Label field")
        val typed = "unsaved-label-${System.currentTimeMillis()}"
        labelField.text = typed

        // No pause here on purpose: the whole point is leaving before the ~400ms debounce
        // would otherwise have committed this on its own.
        device.pressBack()

        val saved = runBlocking { prefs.searchLabel.first() }
        assertEquals("expected the label typed right before back to have been saved", typed, saved)
    }

    /** Swipes the Settings list up until [text] is on screen, or gives up after a few tries. */
    private fun scrollDownUntilVisible(text: String, maxSwipes: Int = 15): Boolean {
        if (device.wait(Until.hasObject(By.text(text)), 500L)) return true
        repeat(maxSwipes) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.8f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.2f).toInt(),
                20,
            )
            if (device.wait(Until.hasObject(By.text(text)), 500L)) return true
        }
        return false
    }
}
