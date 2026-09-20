// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.InputDevice
import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.victorialauncher.widget.ClockWidgetProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.rules.TestName

/** Measures real hosted-widget coordinates, not accessibility's clipped visibleBounds. */
@RunWith(AndroidJUnit4::class)
class HomeSafeAreaTest {
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as VictoriaApp
    private var widgetId = -1

    @Before
    fun setUp() {
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
        pref("widget_height_dp", "int", 180)
        pref("widget_pad_top", "int", when (testName.methodName) {
            "alreadyClearMigrationPreservesRenderedWidgetTop" -> 90
            "underBarMigrationMovesRenderedWidgetToSafeEdge" -> 12
            else -> 0
        })
        pref("widget_pad_bottom", "int", 8)
        pref("widget_side_padding_dp", "int", 20)
        pref("now_playing_enabled", "boolean", false)
        pref("hide_status_bar", "boolean", false)
        pref("allow_rotation", "boolean", true)
        pref("welcome_seen", "boolean", true)
        pref("layout_defaults_version", "int", 2)
        runBlocking {
            check(app.prefs.importJson(JSONObject().put("format", 1)
                .put("app", "Victoria Launcher").put("values", values).toString()))
        }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    @After
    fun tearDown() {
        if (widgetId > 0) {
            runBlocking { app.prefs.removeWidgetId(widgetId) }
            app.widgetHost.deleteAppWidgetId(widgetId)
        }
    }

    @Test
    fun topBlockClearsStatusBar() {
        val (widgetTop, statusTop) = awaitVisibleHost()
        Log.i("HomeSafeAreaTest", "B1 widgetTopPx=$widgetTop liveStatusTopPx=$statusTop")
        assertTrue("B1 widgetTopPx=$widgetTop must clear liveStatusTopPx=$statusTop", widgetTop >= statusTop)
    }

    @Test
    fun flingSettlesAtSafeTop() {
        awaitVisibleHost()
        val bounds = hostBounds()
        val device = LauncherTestUtils.uiDevice()
        // Drag on the home background below the short fixture, then let its spring settle.
        val start = (bounds.bottom + 60 * density).toInt().coerceAtMost(device.displayHeight - (100 * density).toInt())
        device.swipe(device.displayWidth / 2, start, device.displayWidth / 2, (start - 160 * density).toInt(), 10)
        SystemClock.sleep(1500)
        val (top, inset) = awaitVisibleHost()
        assertTrue("B2 settled widget top=$top inset=$inset", top >= inset)
    }

    @Test
    fun alreadyClearMigrationPreservesRenderedWidgetTop() {
        val (_, inset) = awaitVisibleHost()
        SystemClock.sleep(500)
        val stored = runBlocking {
            JSONObject(app.prefs.exportJson(stripOtherProfiles = true).json).getJSONObject("values")
        }
        val diagnostic = listOf("widget_pad_top", "layout_defaults_version",
            "layout_safe_top_at_migration", "layout_first_run", "keep_home_off_status_bar")
            .joinToString { key -> "$key=${stored.optJSONObject(key)?.opt("value") ?: "absent"}" }
        Log.i("HomeSafeAreaTest", "B4 clear liveTop=$inset $diagnostic")
        assertEquals("B4 already-clear rendered top; $diagnostic", maxOf(90 * density, inset.toFloat()).toDouble(), hostBounds().top.toDouble(), 2.0)
    }

    @Test
    fun underBarMigrationMovesRenderedWidgetToSafeEdge() {
        val (_, inset) = awaitVisibleHost()
        SystemClock.sleep(500)
        assertEquals("B4 underbar rendered top", maxOf(12 * density, inset.toFloat()).toDouble(), hostBounds().top.toDouble(), 2.0)
    }

    @Test
    fun actualWidgetReceivesTapFourDpInsideTopLeft() {
        awaitVisibleHost()
        val tapped = AtomicBoolean(false)
        instrumentation.runOnMainSync {
            val host = checkNotNull(findHost(activity().window.decorView))
            // Observe the real host's input dispatch, including Android's widget padding.
            // A 4dp point can fall in that padding rather than in the RemoteViews child.
            host.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) tapped.set(true)
                true
            }
        }
        val bounds = hostBounds()
        val x = bounds.left + 4 * density
        val y = bounds.top + 4 * density
        val down = SystemClock.uptimeMillis()
        touch(down, MotionEvent.ACTION_DOWN, x, y)
        touch(down, MotionEvent.ACTION_UP, x, y)
        assertTrue("B3 real hosted widget must receive the tap 4dp inside its top-left", tapped.get())
    }

    @Test
    fun horizontalExtremesKeepActualWidgetInContentWidth() {
        awaitVisibleHost()
        val baseline = hostBounds()
        val contentLeft = baseline.left - 20 * density
        val contentRight = baseline.right + 20 * density
        for (offset in listOf(-200, 200)) {
            runBlocking { app.prefs.setWidgetOffsetXDp(offset) }
            SystemClock.sleep(300)
            val bounds = hostBounds()
            assertTrue("B5 offset=$offset actual left=${bounds.left} contentLeft=$contentLeft", bounds.left >= contentLeft - 1)
            assertTrue("B5 offset=$offset actual right=${bounds.right} contentRight=$contentRight", bounds.right <= contentRight + 1)
        }
    }

    @Test
    fun disablingKeepOffStatusBarRestoresFullBleed() {
        awaitVisibleHost()
        // Reflection keeps this regression test compilable before the preference exists.
        val setter = app.prefs.javaClass.methods.firstOrNull { it.name == "setKeepHomeOffStatusBar" }
        assertTrue("B6 keep-off-status-bar preference must exist", setter != null)
        runBlocking {
            kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
                val result = checkNotNull(setter).invoke(app.prefs, false, continuation)
                if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(Unit))
            }
        }
        SystemClock.sleep(300)
        assertEquals("B6 fullbleed top", 0, hostBounds().top)
    }

    @Test
    fun liveStatusInsetChangesMoveRestingOriginWithoutRecreation() {
        awaitVisibleHost()
        var original: MainActivity? = null
        instrumentation.runOnMainSync {
            original = activity()
            WindowInsetsControllerCompat(checkNotNull(original).window, checkNotNull(original).window.decorView)
                .hide(WindowInsetsCompat.Type.statusBars())
        }
        SystemClock.sleep(500)
        var hiddenSafeTop = 0
        instrumentation.runOnMainSync {
            assertTrue("B8 fixture keeps same activity", original === activity())
            hiddenSafeTop = checkNotNull(ViewCompat.getRootWindowInsets(activity().window.decorView))
                .getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
        }
        assertEquals("B8 hidden bar uses current cutout/status insets", hiddenSafeTop, hostBounds().top)
        instrumentation.runOnMainSync {
            WindowInsetsControllerCompat(activity().window, activity().window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
        SystemClock.sleep(500)
        val (top, inset) = awaitVisibleHost()
        assertTrue("B8 live inset update must protect host", top >= inset)
    }

    private val density get() = app.resources.displayMetrics.density

    private fun activity(): MainActivity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()

    private fun hostBounds(): Rect {
        var result: Rect? = null
        instrumentation.runOnMainSync {
            val host = checkNotNull(findHost(activity().window.decorView))
            val xy = IntArray(2)
            host.getLocationOnScreen(xy)
            result = Rect(xy[0], xy[1], xy[0] + host.width, xy[1] + host.height)
        }
        return checkNotNull(result)
    }

    private fun touch(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }

    private fun awaitVisibleHost(): Pair<Int, Int> {
        var measured: Pair<Int, Int>? = null
        var sampleCount = 0
        var previous: Pair<Int, Int>? = null
        var stableSamples = 0
        var requestedVisibleStatusBar = false
        val deadline = SystemClock.uptimeMillis() + 120_000
        while (measured == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                val activity = resumed.filterIsInstance<MainActivity>().firstOrNull()
                if (activity == null) {
                    if (sampleCount++ % 20 == 0) Log.i("HomeSafeAreaTest", "B1 fixture resumed=${resumed.map { it.javaClass.name }}")
                    return@runOnMainSync
                }
                val root = activity.window.decorView
                val safeTop = ViewCompat.getRootWindowInsets(root)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
                val host = findHost(root)
                if (host != null && host.isShown && !requestedVisibleStatusBar) {
                    // The launcher's asynchronous fader can start before window focus. Make
                    // this fixture's visible-bar precondition explicit after the widget exists.
                    WindowInsetsControllerCompat(activity.window, root)
                        .show(WindowInsetsCompat.Type.statusBars())
                    requestedVisibleStatusBar = true
                }
                if (sampleCount++ % 20 == 0) Log.i("HomeSafeAreaTest", "B1 fixture status=$safeTop id=$widgetId host=${host != null} shown=${host?.isShown} height=${host?.height}")
                if (safeTop > 0 && host != null && host.isShown && host.height > 0) {
                    val xy = IntArray(2)
                    host.getLocationOnScreen(xy)
                    val candidate = xy[1] to safeTop
                    stableSamples = if (candidate == previous) stableSamples + 1 else 0
                    previous = candidate
                    // Require settled coordinates, not a particular expected result. This
                    // still returns y=0 on legacy builds, but lets inset relayout complete.
                    if (stableSamples >= 3) measured = candidate
                }
            }
            if (measured == null) SystemClock.sleep(100)
        }
        return checkNotNull(measured) {
            "fixture did not produce both a real laid-out widget and a live status-bar inset"
        }
    }

    private fun findHost(view: View): AppWidgetHostView? {
        if (view is AppWidgetHostView && view.appWidgetId == widgetId) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findHost(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
