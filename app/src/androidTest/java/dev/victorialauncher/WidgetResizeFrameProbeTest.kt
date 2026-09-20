// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/** A7 measurement probe. Performance acceptance is reviewed from logs on a named device. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeFrameProbeTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun a7_reportsWindowFrameTimesAndFirstVisibleMovementLatency(): Unit = with(fixture) {
        enterResize()
        val originalHeight = bounds().height()
        val firstMoveNs = AtomicLong()
        val firstChangedPreDrawNs = AtomicLong()
        val totalDurationsNs = Collections.synchronizedList(mutableListOf<Long>())
        val droppedReports = AtomicInteger()
        val handlerThread = HandlerThread("widget-resize-frame-probe").apply { start() }
        lateinit var window: Window
        lateinit var root: View
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            if (firstMoveNs.get() > 0 && hostOnMain().height != originalHeight) {
                firstChangedPreDrawNs.compareAndSet(0, System.nanoTime())
            }
            true
        }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            // FrameMetrics is reused by Android: copy primitive values in the callback.
            if (firstMoveNs.get() > 0) {
                totalDurationsNs.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                droppedReports.addAndGet(dropped)
            }
        }
        instrumentation.runOnMainSync {
            window = activityOnMain().window
            root = window.decorView
            root.viewTreeObserver.addOnPreDrawListener(preDraw)
            window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper))
        }
        try {
            val started = System.nanoTime()
            drag(BOTTOM_HANDLE, 100f, moves = 120,
                beforeFirstMove = { firstMoveNs.set(System.nanoTime()) })
            await("First changed widget layout reaches pre-draw") { firstChangedPreDrawNs.get() > 0 }
            await("Window frame metrics arrive") { totalDurationsNs.isNotEmpty() }
            SystemClock.sleep(100)
            val samples = synchronized(totalDurationsNs) { totalDurationsNs.toList().sorted() }
            fun percentile(p: Double): Double = samples[(ceil(samples.size * p).toInt() - 1)
                .coerceIn(samples.indices)] / 1_000_000.0
            val firstVisibleMs = (firstChangedPreDrawNs.get() - firstMoveNs.get()) / 1_000_000.0
            assertTrue("The drag must produce a changed rendered layout", firstVisibleMs >= 0)
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            val jankCount = samples.count { it > 16_600_000L }
            Log.i("WidgetResizeFrameProbe", "A7 moves=120 frames=${samples.size} seconds=$seconds jankCount=$jankCount " +
                "p50TotalMs=${percentile(.50)} p95TotalMs=${percentile(.95)} " +
                "maxTotalMs=${samples.last() / 1_000_000.0} " +
                "firstChangedPreDrawLatencyMs=$firstVisibleMs " +
                "droppedMetricReports=${droppedReports.get()} density=$density " +
                "displayPx=${device.displayWidth}x${device.displayHeight}")
            // PLAN4's explicit emulator budgets, not a promise about all physical devices.
            // First changed pre-draw is reported honestly; recording verifies presentation.
            assertTrue("A7 requires zero frames over16.6ms; observed $jankCount", jankCount == 0)
            assertTrue("A7 first visible movement must be under2frames; observed ${firstVisibleMs}ms",
                firstVisibleMs < 33.2)
        } finally {
            instrumentation.runOnMainSync {
                root.viewTreeObserver.removeOnPreDrawListener(preDraw)
                window.removeOnFrameMetricsAvailableListener(listener)
            }
            handlerThread.quitSafely()
        }
    }
}
