// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Review regressions use real rendered host bounds, independent of the resize preview state. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeReviewRegressionTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun externalHeightUpdatesWhileIdleReachTheRenderedHostAfterResize() = with(fixture) {
        enterResize()
        val initialHeight = heightPref()
        drag(BOTTOM_HANDLE, 40f)
        await("Resize commits before the external update") { heightPref() == initialHeight + 40 }
        device.pressBack()
        assertTrue("Resize is idle", device.wait(Until.gone(By.desc(BOTTOM_HANDLE)), 3_000L))
        val idleTop = bounds().top
        // A measurement-time supplier must see each new stored value, not retain the
        // previous gesture preview or a lambda capturing the first preference value.
        for (height in listOf(initialHeight + 80, initialHeight - 40)) {
            runBlocking { app.prefs.setWidgetHeightDp(height) }
            await("Idle external height $height reaches actual AppWidgetHostView bounds") {
                abs(bounds().height() / density - height) <= 2f
            }
            assertDp("External height update keeps the rendered top fixed",
                idleTop / density, bounds().top / density)
            assertFalse("External preference updates do not enter resize",
                device.hasObject(By.desc(BOTTOM_HANDLE)))
        }
    }

    @Test fun noOpHandleTapThenExitDoesNotOverrideLaterEditLayoutHeight() = with(fixture) {
        enterResize()
        val initialHeight = heightPref()
        val handle = handle(BOTTOM_HANDLE)
        assertTrue(device.click(handle.centerX(), handle.centerY()))
        SystemClock.sleep(150)
        assertEquals("No-op tap leaves persistent height unchanged", initialHeight, heightPref())
        device.pressBack()
        assertTrue("Back exits in-place resize", device.wait(Until.gone(By.desc(BOTTOM_HANDLE)), 3_000L))
        setHeightThroughEditLayout(initialHeight + 60)
        await("Edit layout height reaches actual host after a no-op resize") {
            abs(bounds().height() / density - (initialHeight + 60)) <= 2f
        }
        assertDp("Actual host follows the new Edit layout height", (initialHeight + 60).toFloat(),
            bounds().height() / density)
    }

    @Test fun cancelledHandleDragRestoresLayoutAndDoesNotOverrideLaterEditLayoutHeight() = with(fixture) {
        enterResize()
        val initial = bounds()
        val initialHeight = heightPref()
        val initialTop = topPref()
        val handle = handle(BOTTOM_HANDLE)
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, handle.exactCenterX(), handle.exactCenterY())
        try {
            for (index in 1..12) {
                SystemClock.sleep(16)
                inject(downTime, MotionEvent.ACTION_MOVE, handle.exactCenterX(),
                    handle.exactCenterY() + 40f * density * index / 12)
            }
            await("Cancelled gesture fixture first produces a real 40dp preview") {
                abs((bounds().height() - initial.height()) / density - 40f) <= 2f
            }
        } finally {
            inject(downTime, MotionEvent.ACTION_CANCEL, handle.exactCenterX(), handle.exactCenterY() + 40f * density)
        }
        await("Cancellation restores the original rendered height") {
            abs(bounds().height() / density - initialHeight) <= 2f
        }
        assertEquals("Cancellation never persists preview height", initialHeight, heightPref())
        assertEquals("Cancellation preserves top padding", initialTop, topPref())
        device.pressBack()
        assertTrue("Back exits cancelled resize", device.wait(Until.gone(By.desc(BOTTOM_HANDLE)), 3_000L))
        setHeightThroughEditLayout(initialHeight + 60)
        await("Edit layout height reaches actual host after cancellation") {
            abs(bounds().height() / density - (initialHeight + 60)) <= 2f
        }
        assertDp("Cancelled preview cannot mask a later stored height", (initialHeight + 60).toFloat(),
            bounds().height() / density)
    }

    @Test fun shrinkingAtBottomScrollLimitTracksEveryMoveWithoutMovingOppositeEdge() = with(fixture) {
        runBlocking {
            app.prefs.setSwipeUpOpensList(false)
            app.prefs.setWidgetHeightDp(900)
            app.prefs.setHomePadding(dev.victorialauncher.data.PaddingSlot.WIDGET_BOTTOM, 200)
        }
        await("Overflow fixture has an actual 900dp host") { abs(bounds().height() / density - 900f) <= 2f }
        assertTrue("The complete widget block, including its trailing gap, must overflow the viewport",
            bounds().bottom + 200f * density > device.displayHeight)
        var lastBottom = bounds().bottom
        var consecutiveStationarySwipes = 0
        var reachedBottom = false
        for (attempt in 1..8) {
            device.swipe(device.displayWidth / 2, (device.displayHeight * .76f).toInt(),
                device.displayWidth / 2, (device.displayHeight * .28f).toInt(), 70)
            // Let the documented spring settle, then prove saturation with another swipe.
            SystemClock.sleep(700)
            val currentBottom = bounds().bottom
            consecutiveStationarySwipes = if (abs(currentBottom - lastBottom) <= 2) {
                consecutiveStationarySwipes + 1
            } else 0
            lastBottom = currentBottom
            if (consecutiveStationarySwipes >= 2) {
                reachedBottom = true
                break
            }
        }
        assertTrue("Two additional upward swipes must prove the stack reached minOffset", reachedBottom)
        val before = bounds()
        assertTrue("The stack is actually scrolled past its top", before.top < 0)
        assertTrue("The bottom edge remains visible and reachable",
            before.bottom > 120f * density && before.bottom < device.displayHeight - 16f * density)
        enterResizeOnVisiblePart()
        val handle = handle(BOTTOM_HANDLE)
        assertTrue("Visible bottom handle supplies the drag target", !handle.isEmpty)
        drag(BOTTOM_HANDLE, -60f, moves = 36) { index, sentDp ->
            await("MOVE $index: bottom follows $sentDp dp and opposite edge stays fixed", 1_000) {
                val sample = bounds()
                abs((sample.bottom - before.bottom) / density - sentDp) <= 2f &&
                    abs((sample.top - before.top) / density) <= 2f
            }
            val sample = bounds()
            assertDp("MOVE $index actual bottom follows finger", sentDp,
                (sample.bottom - before.bottom) / density)
            assertDp("MOVE $index actual opposite edge is stationary", before.top / density, sample.top / density)
        }
        await("Scrolled shrink persists 840dp") { heightPref() == 840 }
        assertDp("Release keeps opposite edge fixed", before.top / density, bounds().top / density)
        assertDp("Release retains the 60dp bottom displacement", -60f, (bounds().bottom - before.bottom) / density)
    }

    @Test fun firstRunComputedFavoritesCenterDoesNotJumpWhenResizePersists() = with(fixture) {
        importFreshLayoutWithSyntheticFavorite()
        assertFalse("Fresh-layout fixture must have no padding preference",
            runBlocking { app.prefs.hasCustomLayout.first() })
        assertTrue("Fresh-layout fixture remains eligible for computed centering after migration",
            runBlocking { app.prefs.isFirstRunLayout.first() })
        await("Synthetic favorite becomes visible") { device.hasObject(By.text(FAVORITE_LABEL)) }
        await("Computed favorites gap is materially larger than the default 8dp gap") {
            favoriteBounds().top - bounds().bottom > 40f * density
        }
        SystemClock.sleep(150)
        val initialFavorite = favoriteBounds()
        assertTrue("Synthetic favorite is centered within the display, not merely below the widget",
            abs(initialFavorite.exactCenterY() - device.displayHeight / 2f) <= 40f * density)
        enterResizeOnVisiblePart()
        var beforeReleaseFavorite: Rect? = null
        var beforeReleaseWidget: Rect? = null
        drag(BOTTOM_HANDLE, 40f, moves = 36) { index, _ ->
            if (index == 36) {
                await("Final preview is rendered while finger is still down") {
                    abs(bounds().height() / density - 220f) <= 2f
                }
                SystemClock.sleep(100)
                beforeReleaseFavorite = favoriteBounds()
                beforeReleaseWidget = bounds()
                assertFalse("Preview has not retired computed favorites centering",
                    runBlocking { app.prefs.hasCustomLayout.first() })
            }
        }
        await("Fresh-layout resize is persisted") { heightPref() == 220 }
        val favoriteAtRelease = checkNotNull(beforeReleaseFavorite)
        val widgetAtRelease = checkNotNull(beforeReleaseWidget)
        repeat(12) { sample ->
            assertDp("After persistence sample $sample: favorite top cannot jump",
                favoriteAtRelease.top / density, favoriteBounds().top / density)
            assertDp("After persistence sample $sample: widget top cannot jump",
                widgetAtRelease.top / density, bounds().top / density)
            assertDp("After persistence sample $sample: widget bottom cannot jump",
                widgetAtRelease.bottom / density, bounds().bottom / density)
            SystemClock.sleep(32)
        }
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.desc(BOTTOM_HANDLE)), 3_000L))
        assertDp("Exiting resize preserves the favorites' final preview position",
            favoriteAtRelease.top / density, favoriteBounds().top / density)
    }

    private fun setHeightThroughEditLayout(height: Int) = with(fixture) {
        enterResize()
        val more = device.wait(Until.findObject(By.desc("More")), 2_000L)
            ?: device.wait(Until.findObject(By.text("More")), 2_000L)
        assertTrue("More is reachable", more != null)
        checkNotNull(more).click()
        val edit = device.wait(Until.findObject(By.text(string(R.string.action_edit_layout))), 3_000L)
        assertTrue("Edit layout remains reachable", edit != null)
        checkNotNull(edit).click()
        repeat(3) {
            if (!device.hasObject(By.text(string(R.string.handle_widget_height)))) {
                device.swipe(device.displayWidth / 2, (device.displayHeight * .82f).toInt(),
                    device.displayWidth / 2, (device.displayHeight * .30f).toInt(), 45)
            }
        }
        assertTrue("Widget-height editor is visible", device.hasObject(By.text(string(R.string.handle_widget_height))))
        val value = device.findObject(By.text("${heightPref()}dp"))
        assertTrue("Stored widget-height numeric value is reachable", value != null)
        checkNotNull(value).click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000L)
        assertTrue("Numeric height entry opens", input != null)
        checkNotNull(input).text = height.toString()
        val save = device.findObject(By.text(string(R.string.action_save)))
        assertTrue("Height entry Save is reachable", save != null)
        checkNotNull(save).click()
        await("Edit layout saves requested height") { heightPref() == height }
    }

    /** Unlike the ordinary fixture, this also supports a widget whose top is off screen. */
    private fun enterResizeOnVisiblePart() = with(fixture) {
        val before = bounds()
        val x = before.exactCenterX()
        val visibleTop = maxOf(before.top.toFloat(), 80f * density)
        val visibleBottom = minOf(before.bottom.toFloat(), device.displayHeight - 80f * density)
        assertTrue("Widget has a visible interior to long-press", visibleBottom - visibleTop > 24f * density)
        val y = (visibleTop + visibleBottom) / 2f
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        inject(downTime, MotionEvent.ACTION_UP, x, y)
        handle(BOTTOM_HANDLE)
        assertFalse("Long press stays out of full Edit layout",
            device.hasObject(By.text(string(R.string.handle_side_padding))))
        assertDp("Entering resize preserves actual top", before.top / density, bounds().top / density)
        assertDp("Entering resize preserves actual bottom", before.bottom / density, bounds().bottom / density)
    }

    private fun importFreshLayoutWithSyntheticFavorite() = with(fixture) {
        val values = JSONObject()
        fun pref(name: String, type: String, value: Any) {
            values.put(name, JSONObject().put("type", type).put("value", value))
        }
        pref("widget_ids", "string", widgetId.toString())
        pref("widget_id", "int", widgetId)
        pref("widget_position", "int", 0)
        pref("widget_height_dp", "int", 180)
        pref("favorites_order", "string", "folder:resize-centered-fixture")
        pref("folders_json", "string", JSONArray().put(JSONObject()
            .put("id", "resize-centered-fixture").put("name", FAVORITE_LABEL)
            .put("apps", JSONArray())).toString())
        pref("home_header_enabled", "boolean", false)
        pref("now_playing_enabled", "boolean", false)
        pref("hide_status_bar", "boolean", false)
        pref("welcome_seen", "boolean", true)
        pref("niagara_offer_seen", "boolean", true)
        pref("layout_defaults_version", "int", 1)
        // No *_pad_* keys: writing any of them would defeat the regression's precondition.
        runBlocking { check(app.prefs.importJson(JSONObject().put("format", 1).put("values", values).toString())) }
        LauncherTestUtils.goHome()
    }

    private fun favoriteBounds(): Rect {
        val node = fixture.device.findObject(By.text(FAVORITE_LABEL))
        assertTrue("Synthetic favorite remains visible", node != null)
        return checkNotNull(node).visibleBounds
    }

    private fun inject(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue("Touch event injection succeeds", fixture.instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    companion object {
        private const val FAVORITE_LABEL = "Resize centered fixture"
    }
}
