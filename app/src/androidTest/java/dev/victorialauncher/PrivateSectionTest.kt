// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.USER_TYPE_PROFILE_PRIVATE
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box tests for how the "Private space" and "Vicky+" tail sections lay out relative to
 * the A-Z list (DESIGN2 §1, dev.victorialauncher.ui.applist.AppListModel.buildSectionedRows).
 *
 * Shares PrivateSpaceLauncherTest's device/app fixture and reasoning (same private-space app,
 * taken away from the main profile so its name is unambiguous — see that class's doc comment),
 * but its setup helpers are private to it, so the parts needed here are reproduced rather than
 * inherited.
 */
@RunWith(AndroidJUnit4::class)
class PrivateSectionTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device: UiDevice get() = LauncherTestUtils.uiDevice()
    private val userManager: UserManager get() = context.getSystemService(UserManager::class.java)
    private val launcherApps: LauncherApps get() = context.getSystemService(LauncherApps::class.java)

    private lateinit var privateUser: UserHandle

    @Before
    fun setUp() {
        assumeTrue("private space arrived in Android 15", Build.VERSION.SDK_INT >= 35)
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
        // Whichever test method runs first pays for a real, uncancellable first composition of
        // a freshly (re)installed process (see VickyButtonTest's setUp comment for the full
        // reasoning) — on a loaded/shared emulator host that can badly outrun a normal
        // UI-interaction timeout. Paying for it once here, before any of the private-space
        // setup below, keeps that cold-boot cost off whichever test happens to run first.
        check(device.wait(Until.hasObject(By.desc(BUTTON_DESC)), READY_WAIT_MS)) {
            "the launcher's home screen never composed after going home"
        }
        val user = privateProfile()
        assumeTrue("this device has no private space", user != null)
        privateUser = user!!
        assumeTrue(
            "$PRIVATE_APP_PACKAGE is not installed in the private space",
            activityIn(privateUser) != null,
        )
        setLocked(false)
        // Taken away from the main profile so a "Clock" row can only have come from the
        // private one. Given back in tearDown, whatever the test did.
        device.executeShellCommand("pm uninstall --user 0 $PRIVATE_APP_PACKAGE")
    }

    @After
    fun tearDown() {
        if (!this::privateUser.isInitialized) return
        device.executeShellCommand("pm install-existing --user 0 $PRIVATE_APP_PACKAGE")
        setLocked(false)
    }

    @Test
    fun lockedListEndsWithPrivateHeaderThenPadlockThenVickyPlusThenItsTwoRows() {
        setLocked(true)
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        scrollListToBottom()

        // A locked space has no app rows of its own (AppListModel.buildSectionedRows §2), so
        // the whole tail is exactly these five rows, in this order. Each row's top is read
        // immediately after it is found — holding a UiObject2 across a later query risks
        // androidx.test.uiautomator.StaleObjectException once the list has recomposed again.
        val privateHeaderTop = requireVisible(PRIVATE_SECTION_TITLE).visibleBounds.top
        val padlockTop = requireVisible(UNLOCK_LABEL).visibleBounds.top
        val launcherHeaderTop = requireVisible(LAUNCHER_SECTION_TITLE).visibleBounds.top
        val recentTop = requireVisible(RECENT_LABEL).visibleBounds.top
        val settingsTop = requireVisible(SETTINGS_LABEL).visibleBounds.top

        assertTrue(
            "expected the padlock row immediately under the Private space header",
            privateHeaderTop < padlockTop,
        )
        assertTrue(
            "expected the Vicky+ header right after the padlock (a locked space has no app rows)",
            padlockTop < launcherHeaderTop,
        )
        assertTrue(
            "expected Recently installed above Vicky+ settings under the Vicky+ header",
            launcherHeaderTop < recentTop && recentTop < settingsTop,
        )
    }

    @Test
    fun noPrivateAppLabelAppearsAnywhereWhileLocked() {
        setLocked(true)
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        scrollListToTop()

        for (step in 0 until MAX_SCROLL_STEPS) {
            assertFalse(
                "a locked private space's app label was visible in the list",
                device.hasObject(By.text(PRIVATE_APP_LABEL)),
            )
            if (device.hasObject(By.text(SETTINGS_LABEL))) break
            swipeListUp()
        }
        assertFalse(
            "a locked private space's app label was visible at the bottom of the list",
            device.hasObject(By.text(PRIVATE_APP_LABEL)),
        )
    }

    @Test
    fun unlockedPrivateAppsAppearAfterTheHeaderAndNotUnderTheirLetter() {
        setLocked(false)
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        scrollListToTop()

        // Phase 1 — walk down from the top with the same single-condition-per-step scan
        // noPrivateAppLabelAppearsAnywhereWhileLocked uses (proven reliable there): the private
        // app must never turn up before the header that introduces its section, however many
        // A-Z letters sort ahead of it. Stop the moment the header itself is on screen — there
        // is nothing further this phase needs to prove.
        for (step in 0 until MAX_SCROLL_STEPS) {
            if (device.hasObject(By.text(PRIVATE_SECTION_TITLE))) break
            assertFalse(
                "the private app was listed in the A-Z portion, above its own section",
                device.hasObject(By.text(PRIVATE_APP_LABEL)),
            )
            swipeListUp()
        }
        assertTrue(
            "never reached the Private space header",
            device.hasObject(By.text(PRIVATE_SECTION_TITLE)),
        )

        // Phase 2 — ordering: scroll on to the bottom (same helper the locked-state ordering
        // test uses) and read each row's position immediately after finding it, since a
        // UiObject2 goes stale if the list scrolls again before its bounds are read.
        scrollListToBottom()
        val headerTop = requireVisible(PRIVATE_SECTION_TITLE).visibleBounds.top
        val padlockTop = requireVisible(LOCK_LABEL).visibleBounds.top
        val appTop = requireVisible(PRIVATE_APP_LABEL).visibleBounds.top
        assertTrue("expected the private app below its section header", headerTop < appTop)
        assertTrue(
            "expected the padlock row above the private app it unlocked",
            padlockTop < appTop,
        )
    }

    private fun requireVisible(text: String): UiObject2 =
        device.wait(Until.findObject(By.text(text)), 5_000L)
            ?: error("expected \"$text\" to be visible")

    /**
     * Was a single long, fast fling (30%-85% of the screen height in 15ms) repeated up to 160
     * times. That drag is large and fast enough that once the list has nothing left to give —
     * which it does almost immediately, since this always runs right after [LauncherTestUtils.openAppList]
     * opens at the top already — the unconsumed motion was landing on the system's own
     * pull-down-for-notifications handling instead of the app: logcat during a failing run
     * shows `CUJ=J<NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE>` frames timed to the swipe loop, and
     * the launcher never gets the rest of the gesture back, so the search that follows never
     * finds anything (`unlockedPrivateAppsAppearAfterTheHeaderAndNotUnderTheirLetter`'s "never
     * reached the Private space header" — logged as a suspected gesture race in a previous
     * round, `.claude/STATE.md` row 39: "the list was pulled off its top and the notification
     * shade opened"). `noPrivateAppLabelAppearsAnywhereWhileLocked` only ever asserts absence,
     * so the same failure mode passed there vacuously instead of loudly.
     *
     * Reusing [swipeListUp]'s already-calibrated short, slow drag (same magnitude proven safe
     * at the list's other end) in reverse fixes the cause without weakening what either test
     * checks.
     */
    private fun scrollListToTop() {
        repeat(MAX_SCROLL_STEPS) { swipeListDown() }
    }

    private fun scrollListToBottom() {
        for (step in 0 until MAX_SCROLL_STEPS) {
            if (device.hasObject(By.text(SETTINGS_LABEL))) return
            swipeListUp()
        }
    }

    /**
     * A short, slow drag. Two reasons. A fast fling can jump over the two-row locked section
     * between one poll and the next. And the list closes when it is pulled more than about
     * 90dp past its end (AppListScreen's pull-to-collapse), so the step that finally brings
     * the end into view must not overshoot by more than that: 8% of the screen is about
     * 190px here, under the commit distance, so an overshoot can only stretch, never close.
     */
    private fun swipeListUp() {
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.70f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.62f).toInt(),
            30,
        )
    }

    /** [swipeListUp], reversed: the same short, slow drag, revealing what's above instead. */
    private fun swipeListDown() {
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.62f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.70f).toInt(),
            30,
        )
    }

    private fun privateProfile(): UserHandle? =
        runCatching { launcherApps.profiles }.getOrNull().orEmpty().firstOrNull { user ->
            runCatching { launcherApps.getLauncherUserInfo(user)?.userType }
                .getOrNull() == USER_TYPE_PROFILE_PRIVATE
        }

    private fun activityIn(user: UserHandle) =
        runCatching { launcherApps.getActivityList(PRIVATE_APP_PACKAGE, user) }
            .getOrNull()
            ?.firstOrNull()

    private fun setLocked(locked: Boolean) {
        if (userManager.isQuietModeEnabled(privateUser) == locked) return
        userManager.requestQuietModeEnabled(locked, privateUser)
        check(waitForQuietMode(locked)) { "the private space never became ${if (locked) "locked" else "unlocked"}" }
    }

    private fun waitForQuietMode(expected: Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + QUIET_MODE_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (userManager.isQuietModeEnabled(privateUser) == expected) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private companion object {
        const val PRIVATE_APP_PACKAGE = "com.google.android.deskclock"
        const val PRIVATE_APP_LABEL = "Clock"
        const val PRIVATE_SECTION_TITLE = "Private space"
        const val LAUNCHER_SECTION_TITLE = "Vicky+"
        const val UNLOCK_LABEL = "Unlock private space"
        const val LOCK_LABEL = "Lock private space"
        const val RECENT_LABEL = "Recently installed"
        const val SETTINGS_LABEL = "Vicky+ settings"
        const val MAX_SCROLL_STEPS = 160
        const val QUIET_MODE_MS = 15_000L
        const val BUTTON_DESC = "Vicky+ button"

        /** One-time cold-boot budget for [setUp]; see the comment there. */
        const val READY_WAIT_MS = 120_000L
    }
}
