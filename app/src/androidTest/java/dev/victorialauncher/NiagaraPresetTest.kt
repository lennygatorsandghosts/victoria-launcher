// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.AzStripVisibility
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
 * Black-box tests for the "Apply Niagara style" settings row (DESIGN2 §5,
 * dev.victorialauncher.data.NiagaraPreset): a confirm dialog that, on Apply, sets icon size
 * 26dp / label 18sp / an always-visible A-Z strip in one DataStore transaction, and leaves
 * everything untouched on Cancel.
 */
@RunWith(AndroidJUnit4::class)
class NiagaraPresetTest {

    private val device: UiDevice get() = LauncherTestUtils.uiDevice()
    private lateinit var prefs: Prefs

    private var originalIconSizeDp = 0
    private var originalLabelSizeSp = 0
    private var originalAzStripVisibility = AzStripVisibility.NEVER

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        prefs = Prefs(context)
        runBlocking {
            originalIconSizeDp = prefs.iconSizeDp.first()
            originalLabelSizeSp = prefs.labelSizeSp.first()
            originalAzStripVisibility = prefs.azStripVisibility.first()
        }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
        // Whichever test method runs first pays for a real, uncancellable first composition of
        // a freshly (re)installed process (see VickyButtonTest's setUp comment for the full
        // reasoning) — on a loaded/shared emulator host that can badly outrun a normal
        // UI-interaction timeout. Paying for it once here, before navigating into Settings,
        // keeps that cold-boot cost off whichever test happens to run first.
        check(device.wait(Until.hasObject(By.desc(BUTTON_DESC)), READY_WAIT_MS)) {
            "the launcher's home screen never composed after going home"
        }
        openSettings()
    }

    @After
    fun tearDown() {
        runBlocking {
            prefs.setIconSizeDp(originalIconSizeDp)
            prefs.setLabelSizeSp(originalLabelSizeSp)
            prefs.setAzStripVisibility(originalAzStripVisibility)
        }
    }

    @Test
    fun settingsListsTheApplyRow() {
        assertTrue(
            "expected the Apply Niagara style row in Settings",
            scrollDownUntilVisible(APPLY_ROW_LABEL),
        )
    }

    @Test
    fun tappingShowsTheConfirmDialog() {
        openApplyDialog()
        assertTrue(LauncherTestUtils.waitForText("Apply"))
        assertTrue(LauncherTestUtils.waitForText("Cancel"))
        device.findObject(By.text("Cancel")).click()
    }

    @Test
    fun cancelChangesNothing() {
        openApplyDialog()
        device.findObject(By.text("Cancel")).click()

        assertEquals(
            "Cancel must not change the icon size",
            originalIconSizeDp,
            runBlocking { prefs.iconSizeDp.first() },
        )
        assertEquals(
            "Cancel must not change the label size",
            originalLabelSizeSp,
            runBlocking { prefs.labelSizeSp.first() },
        )
        assertEquals(
            "Cancel must not change the A-Z strip visibility",
            originalAzStripVisibility,
            runBlocking { prefs.azStripVisibility.first() },
        )
    }

    @Test
    fun applySetsIconLabelAndStrip() {
        openApplyDialog()
        device.findObject(By.text("Apply")).click()

        // The write lands in a launched coroutine on the other side of the click, so poll
        // rather than assume it has landed the instant the click returns.
        val deadline = System.currentTimeMillis() + 4_000L
        var azStripVisibility = readAzStripVisibility()
        while (azStripVisibility != AzStripVisibility.ALWAYS && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            azStripVisibility = readAzStripVisibility()
        }

        assertEquals(
            "expected the Niagara preset's always-visible A-Z strip",
            AzStripVisibility.ALWAYS,
            azStripVisibility,
        )
        assertEquals(
            "expected the Niagara preset's 26dp icon size",
            26,
            runBlocking { prefs.iconSizeDp.first() },
        )
        assertEquals(
            "expected the Niagara preset's 18sp label size",
            18,
            runBlocking { prefs.labelSizeSp.first() },
        )
    }

    private fun readAzStripVisibility(): AzStripVisibility = runBlocking { prefs.azStripVisibility.first() }

    private fun openSettings() {
        LauncherTestUtils.openAppList()
        val settingsLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_open_settings)
        // A prefix, so the text typed into the search field is not itself a match for the
        // row's label when the row is looked up next.
        LauncherTestUtils.filterAppList(settingsLabel.take(9))
        assertTrue("expected the Settings row", LauncherTestUtils.waitForText(settingsLabel))
        device.findObject(By.text(settingsLabel)).click()
        assertTrue(
            "expected the Settings screen to open, with the Apply row reachable",
            scrollDownUntilVisible(APPLY_ROW_LABEL),
        )
    }

    private fun openApplyDialog() {
        assertTrue(
            "expected the Apply Niagara style row in Settings",
            scrollDownUntilVisible(APPLY_ROW_LABEL),
        )
        device.findObject(By.text(APPLY_ROW_LABEL)).click()
        assertTrue(
            "expected the Niagara style confirm dialog",
            LauncherTestUtils.waitForText("Niagara style"),
        )
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

    private companion object {
        const val APPLY_ROW_LABEL = "Apply Niagara style"
        const val BUTTON_DESC = "Vicky+ button"

        /** One-time cold-boot budget for [setUp]; see the comment there. */
        const val READY_WAIT_MS = 120_000L
    }
}
