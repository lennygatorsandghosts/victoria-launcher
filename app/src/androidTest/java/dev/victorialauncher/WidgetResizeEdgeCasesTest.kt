// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import dev.victorialauncher.HostedWidgetFixture.Companion.TOP_HANDLE
import dev.victorialauncher.data.PaddingSlot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real input and raw host geometry for app-owned margins and partially visible handles. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeEdgeCasesTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun outsideTapInAppOwnedSafeMarginDismissesResizeImmediately() = with(fixture) {
        runBlocking {
            app.prefs.setKeepHomeOffStatusBar(true)
            app.prefs.setHomeSafeMarginDp(32)
        }
        val usable = liveUsableViewport()
        await("32dp margin is applied to the real host origin") {
            abs(bounds().top - (usable.top + (100 + 32) * density)) <= 2f * density
        }
        val before = bounds()
        val initialHeight = heightPref()
        val initialTop = topPref()
        val x = usable.left + 16f * density
        val y = before.exactCenterY()
        assertTrue("Tap is in the usable viewport, outside system insets", usable.contains(x.toInt(), y.toInt()))
        assertTrue("Tap is inside the app-owned 32dp margin", x > usable.left && x < usable.left + 32f * density)
        assertTrue("Margin tap is outside the actual widget", x < before.left)

        val enteredAt = SystemClock.uptimeMillis()
        enterResize()
        assertTrue("Resize is active immediately before the margin tap", hasHandle(TOP_HANDLE))
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, x, y)
        inject(down, MotionEvent.ACTION_UP, x, y)
        await("App-owned margin tap dismisses resize within 500ms", 500) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == app.packageName &&
                !hasHandle(TOP_HANDLE) && !hasHandle(BOTTOM_HANDLE)
        }
        assertTrue("Dismissal must precede the independent 3s idle timeout",
            SystemClock.uptimeMillis() - enteredAt < 2_500)
        assertEquals("Dismissal preserves stored height", initialHeight, heightPref())
        assertEquals("Dismissal preserves stored top padding", initialTop, topPref())
        assertDp("Dismissal preserves actual widget top", before.top / density, bounds().top / density)
        assertDp("Dismissal preserves actual widget bottom", before.bottom / density, bounds().bottom / density)
    }

    @Test fun zeroMovementTapOnPartlyOffscreenTopHandleDoesNotResizeOrRepad() = with(fixture) {
        runBlocking {
            app.prefs.setKeepHomeOffStatusBar(true)
            app.prefs.setHomeSafeMarginDp(0)
            app.prefs.setSwipeUpOpensList(false)
            app.prefs.setHomePadding(PaddingSlot.WIDGET_BOTTOM, 200)
            app.prefs.setWidgetHeightDp(900)
        }
        await("Overflow fixture renders its complete 900dp host") {
            abs(bounds().height() / density - 900f) <= 2f
        }
        val usable = liveUsableViewport()
        assertTrue("Fixture needs enough real overflow to pan the top past the safe edge",
            bounds().bottom + 200f * density - usable.bottom > 120f * density)
        panHostTopTo(usable.top - 10f * density, usable)
        val before = bounds()
        assertDp("Fixture top is actually 10dp above the current safe edge", -10f,
            (before.top - usable.top) / density)
        assertTrue("Fixture top really lies above the usable viewport", before.top < usable.top)
        assertEquals("Pan preserves the configured 900dp height", 900, heightPref())
        val initialHeight = heightPref()
        val initialTop = topPref()

        val visible = Rect(before)
        assertTrue("A real portion of the widget is visible for the long press", visible.intersect(usable))
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, visible.exactCenterX(), visible.exactCenterY())
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        inject(down, MotionEvent.ACTION_UP, visible.exactCenterX(), visible.exactCenterY())
        val exposedHandle = Rect(handle(TOP_HANDLE))
        assertTrue("Top handle has a reachable part inside the live usable viewport", exposedHandle.intersect(usable))
        assertTrue("The 40dp top handle still exposes approximately 30dp below the safe edge",
            exposedHandle.height() / density in 25f..32f)
        assertDp("Entering resize does not repair or move the partly offscreen top", before.top / density,
            bounds().top / density)
        val tapX = exposedHandle.exactCenterX()
        val tapY = exposedHandle.exactCenterY()
        assertTrue("Tap hits only the accessible part, beyond live system insets",
            usable.contains(tapX.toInt(), tapY.toInt()))
        val tapDown = SystemClock.uptimeMillis()
        inject(tapDown, MotionEvent.ACTION_DOWN, tapX, tapY)
        SystemClock.sleep(40)
        // Identical absolute DOWN/UP coordinates and no MOVE events: delta is exactly zero.
        inject(tapDown, MotionEvent.ACTION_UP, tapX, tapY)
        repeat(12) { sample ->
            assertEquals("Zero-delta sample $sample cannot shrink stored height", initialHeight, heightPref())
            assertEquals("Zero-delta sample $sample cannot add top padding", initialTop, topPref())
            val actual = bounds()
            assertDp("Zero-delta sample $sample preserves actual top", before.top / density, actual.top / density)
            assertDp("Zero-delta sample $sample preserves actual bottom", before.bottom / density, actual.bottom / density)
            assertDp("Zero-delta sample $sample preserves actual height", before.height() / density, actual.height() / density)
            SystemClock.sleep(32)
        }
    }

    /** Feedback controls real touch input, not application state or fabricated geometry. */
    private fun panHostTopTo(targetTop: Float, usable: Rect) = with(fixture) {
        val x = bounds().exactCenterX()
        var y = usable.top + usable.height() * .65f
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, x, y)
        var reached = false
        try {
            for (move in 1..48) {
                val error = targetTop - bounds().top
                if (abs(error) <= 1f * density) {
                    reached = true
                    break
                }
                y += error.coerceIn(-6f * density, 6f * density)
                assertTrue("Pan touch stays within the actual usable viewport", usable.contains(x.toInt(), y.toInt()))
                inject(down, MotionEvent.ACTION_MOVE, x, y)
                SystemClock.sleep(32)
            }
            // Repeated stationary samples zero the release velocity, avoiding a fling that
            // would obscure the intended partially visible-handle precondition.
            repeat(3) {
                SystemClock.sleep(100)
                inject(down, MotionEvent.ACTION_MOVE, x, y)
            }
        } finally {
            inject(down, MotionEvent.ACTION_UP, x, y)
        }
        assertTrue("Real pan reaches its target before release", reached)
        SystemClock.sleep(700)
        repeat(4) {
            assertDp("Settled pan leaves the actual top at the requested coordinate", targetTop / density,
                bounds().top / density)
            SystemClock.sleep(50)
        }
    }

    /** Hardware-safe screen coordinates, before the optional app-owned margin is added. */
    private fun liveUsableViewport(): Rect = with(fixture) {
        var result: Rect? = null
        instrumentation.runOnMainSync {
            val root = activityOnMain().window.decorView
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(root)) { "Live WindowInsets are required" }
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            assertTrue("Fixture has a live visible status bar", status.top > 0)
            val xy = IntArray(2)
            root.getLocationOnScreen(xy)
            result = Rect(xy[0] + maxOf(cutout.left, navigation.left),
                xy[1] + maxOf(status.top, cutout.top),
                xy[0] + root.width - maxOf(cutout.right, navigation.right),
                xy[1] + root.height - maxOf(navigation.bottom, gestures.bottom))
        }
        checkNotNull(result)
    }

    /** Direct snapshots avoid UiAutomator idle waits accidentally proving timeout dismissal. */
    private fun hasHandle(description: String): Boolean {
        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.contentDescription?.toString() == description) return true
            for (index in 0 until node.childCount) if (find(node.getChild(index))) return true
            return false
        }
        return find(fixture.instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun inject(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue("Real touch event injection succeeds", fixture.instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }
}
