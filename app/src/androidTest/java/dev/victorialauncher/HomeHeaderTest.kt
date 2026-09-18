// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.text.format.DateFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.ui.home.headerText
import java.time.LocalDate
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box tests for the home-screen date/battery header (DESIGN2 §4,
 * [dev.victorialauncher.ui.home.HomeHeader]).
 *
 * The expected date text is computed with the exact same pure helper the header itself calls —
 * [headerText] — fed the same `DateFormat.getBestDateTimePattern(locale, "EEEMMMd")` pattern, so
 * this does not hardcode a format that could drift from production and pass anyway.
 *
 * The header renders the date and the battery percentage as two separate Text nodes (the date's
 * own [headerText] call is always passed a null battery), so they are checked as two separate
 * on-screen texts rather than one combined string.
 */
@RunWith(AndroidJUnit4::class)
class HomeHeaderTest {

    private val device: UiDevice get() = LauncherTestUtils.uiDevice()
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        prefs = Prefs(context)
        runBlocking { prefs.setHomeHeaderEnabled(true) }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()

        // Whichever test method the runner happens to run first pays for a real, uncancellable
        // first composition of a freshly (re)installed process (see VickyButtonTest's setUp
        // comment for the full reasoning and SearchSettingsSaveOnLeaveTest's CR-3 doc comment
        // for why that first composition is expensive) — on a loaded/shared emulator host that
        // can badly outrun a normal UI-interaction timeout. Paying for it once here, with the
        // header left at its default-enabled state, keeps that cold-boot cost from landing
        // unevenly on whichever test happens to run first.
        check(device.wait(Until.hasObject(By.text(expectedDateText())), READY_WAIT_MS)) {
            "the header's date text never appeared after going home"
        }
    }

    @After
    fun tearDown() {
        runBlocking { prefs.setHomeHeaderEnabled(true) }
    }

    private fun expectedDateText(): String {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMd")
        return headerText(LocalDate.now(), locale, null, pattern)
    }

    @Test
    fun theHeaderShowsTodaysDateAndABatteryPercentage() {
        val expected = expectedDateText()
        assertTrue(
            "expected the header's date text \"$expected\"",
            LauncherTestUtils.waitForText(expected),
        )
        assertTrue(
            "expected a battery percentage shown beside the date",
            device.wait(Until.hasObject(By.text(Pattern.compile(PERCENT_PATTERN))), 5_000L),
        )
    }

    @Test
    fun disablingThePrefRemovesTheHeader() {
        runBlocking { prefs.setHomeHeaderEnabled(false) }
        LauncherTestUtils.goHome()

        assertFalse(
            "the header's date text was still shown after disabling home_header_enabled",
            LauncherTestUtils.waitForText(expectedDateText(), ABSENCE_MS),
        )
        assertFalse(
            "a battery percentage was still shown after disabling home_header_enabled",
            device.wait(Until.hasObject(By.text(Pattern.compile(PERCENT_PATTERN))), ABSENCE_MS),
        )
    }

    private companion object {
        const val PERCENT_PATTERN = "^\\d{1,3}%$"
        const val ABSENCE_MS = 3_000L

        /** One-time cold-boot budget for [setUp]; see the comment there. */
        const val READY_WAIT_MS = 120_000L
    }
}
