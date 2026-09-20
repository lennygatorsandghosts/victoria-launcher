// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.victorialauncher.widget.ClockWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Real widget fixture for disposable instrumented-test devices. Replaces test app settings. */
internal class HostedWidgetFixture {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val app get() = instrumentation.targetContext.applicationContext as VictoriaApp
    val device = LauncherTestUtils.uiDevice()
    val density get() = app.resources.displayMetrics.density
    var widgetId = -1
        private set

    fun start(heightDp: Int = 180, topDp: Int = 100) {
        widgetId = app.widgetHost.allocateAppWidgetId()
        val manager = AppWidgetManager.getInstance(app)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            check(manager.bindAppWidgetIdIfAllowed(widgetId, ClockWidgetProvider.componentName(app))) {
                "fixture could not bind the real clock widget"
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        ClockWidgetProvider.render(app, manager, widgetId)
        val values = JSONObject()
        fun pref(name: String, type: String, value: Any) {
            values.put(name, JSONObject().put("type", type).put("value", value))
        }
        pref("widget_ids", "string", widgetId.toString())
        pref("widget_id", "int", widgetId)
        pref("widget_position", "int", 0)
        pref("widget_height_dp", "int", heightDp)
        pref("widget_pad_top", "int", topDp)
        pref("widget_pad_bottom", "int", 8)
        pref("home_header_enabled", "boolean", false)
        pref("now_playing_enabled", "boolean", false)
        pref("hide_status_bar", "boolean", false)
        pref("welcome_seen", "boolean", true)
        pref("niagara_offer_seen", "boolean", true)
        pref("layout_defaults_version", "int", 2)
        runBlocking {
            check(app.prefs.importJson(JSONObject().put("format", 1).put("values", values).toString()))
        }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
        await("real clock host with fixture height") {
            boundsOrNull()?.let { kotlin.math.abs(it.height() / density - heightDp) <= 2f } == true
        }
        // Launcher fader updates are asynchronous. Make the visible-bar fixture explicit
        // after layout, then require a live inset instead of assuming the preference won.
        instrumentation.runOnMainSync {
            val activity = activityOnMain()
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
        await("visible status bar for deterministic resize geometry") {
            var visible = false
            instrumentation.runOnMainSync {
                visible = ViewCompat.getRootWindowInsets(activityOnMain().window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?.let { it > 0 } == true
            }
            visible
        }
    }

    fun close() {
        device.pressHome()
        if (widgetId > 0) {
            runBlocking { app.prefs.removeWidgetId(widgetId) }
            app.widgetHost.deleteAppWidgetId(widgetId)
            widgetId = -1
        }
    }

    fun string(id: Int): String = app.getString(id)
    fun heightPref(): Int = runBlocking { app.prefs.widgetHeightDp.first() }
    fun topPref(): Int = runBlocking { app.prefs.homePaddings.first().widgetTop }

    fun activityOnMain(): MainActivity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        ?: error("No resumed launcher activity; ActivityScenario must not be used across HOME")

    fun bounds(): Rect = checkNotNull(boundsOrNull()) { "Real AppWidgetHostView is not laid out" }

    fun hostOnMain(): AppWidgetHostView = checkNotNull(findHost(activityOnMain().window.decorView)) {
        "Real AppWidgetHostView is missing"
    }

    private fun boundsOrNull(): Rect? {
        var found: Rect? = null
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                ?: return@runOnMainSync
            val host = findHost(activity.window.decorView) ?: return@runOnMainSync
            if (host.isShown && host.height > 0) {
                val xy = IntArray(2)
                host.getLocationOnScreen(xy)
                found = Rect(xy[0], xy[1], xy[0] + host.width, xy[1] + host.height)
            }
        }
        return found
    }

    private fun findHost(view: View): AppWidgetHostView? {
        if (view is AppWidgetHostView && view.appWidgetId == widgetId) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findHost(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** The handle coordinates may use accessibility; geometry assertions always use the host. */
    fun handle(description: String): Rect {
        val node = device.wait(Until.findObject(By.desc(description)), 5_000L)
        assertTrue("Missing accessibility handle: $description", node != null)
        return checkNotNull(node).visibleBounds
    }

    fun enterResize() {
        val original = bounds()
        val downTime = SystemClock.uptimeMillis()
        touch(downTime, MotionEvent.ACTION_DOWN, original.exactCenterX(), original.exactCenterY())
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        touch(downTime, MotionEvent.ACTION_UP, original.exactCenterX(), original.exactCenterY())
        handle(TOP_HANDLE)
        handle(BOTTOM_HANDLE)
        assertFalse("Long press must not enter full Edit layout",
            device.hasObject(By.text(string(R.string.handle_side_padding))))
        assertFalse("Full-layout widget height stepper must stay out of in-place resize",
            device.hasObject(By.text(string(R.string.handle_widget_height))))
        val resized = bounds()
        assertDp("Long press leaves widget top in place", original.top / density, resized.top / density)
        assertDp("Long press leaves widget bottom in place", original.bottom / density, resized.bottom / density)
    }

    /** Each callback sees the actual fraction sent, before UP. At 24/36, 100dp means 66.67dp. */
    fun drag(description: String, deltaDp: Float, moves: Int = 36,
             beforeFirstMove: () -> Unit = {},
             afterMove: (index: Int, sentDp: Float) -> Unit = { _, _ -> }) {
        val handle = handle(description)
        val x = handle.exactCenterX()
        val y = handle.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        touch(downTime, MotionEvent.ACTION_DOWN, x, y)
        var currentY = y
        try {
            for (index in 1..moves) {
                SystemClock.sleep(16)
                val sentDp = deltaDp * index / moves
                currentY = y + sentDp * density
                if (index == 1) beforeFirstMove()
                touch(downTime, MotionEvent.ACTION_MOVE, x, currentY)
                afterMove(index, sentDp)
            }
        } finally {
            touch(downTime, MotionEvent.ACTION_UP, x, currentY)
        }
    }

    private fun touch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue("Touch event injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    fun await(message: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(16)
        }
        assertTrue("Timed out: $message", condition())
    }

    fun assertDp(message: String, expected: Float, actual: Float) {
        assertEquals(message, expected.toDouble(), actual.toDouble(), 2.0)
    }

    companion object {
        const val TOP_HANDLE = "Resize widget top"
        const val BOTTOM_HANDLE = "Resize widget bottom"
    }
}
