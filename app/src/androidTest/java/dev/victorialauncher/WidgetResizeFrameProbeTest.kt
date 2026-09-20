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
        val finishedNs = AtomicLong()
        val firstChangedPreDrawNs = AtomicLong()
        val totalDurationsNs = Collections.synchronizedList(mutableListOf<Long>())
        val phaseDurations = Collections.synchronizedList(mutableListOf<List<Long>>())
        val phases = listOf(FrameMetrics.UNKNOWN_DELAY_DURATION, FrameMetrics.INPUT_HANDLING_DURATION,
            FrameMetrics.ANIMATION_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION, FrameMetrics.DRAW_DURATION,
            FrameMetrics.SYNC_DURATION, FrameMetrics.COMMAND_ISSUE_DURATION, FrameMetrics.SWAP_BUFFERS_DURATION)
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
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (firstMoveNs.get() > 0 && vsync >= firstMoveNs.get() &&
                (finishedNs.get() == 0L || vsync <= finishedNs.get())) {
                totalDurationsNs.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                phaseDurations.add(phases.map { metrics.getMetric(it) })
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
            dragForDuration(BOTTOM_HANDLE, 100f,
                beforeFirstMove = { firstMoveNs.set(System.nanoTime()) })
            finishedNs.set(System.nanoTime())
            await("First changed widget layout reaches pre-draw") { firstChangedPreDrawNs.get() > 0 }
            await("Window frame metrics arrive") { totalDurationsNs.isNotEmpty() }
            SystemClock.sleep(100)
            val samples = synchronized(totalDurationsNs) { totalDurationsNs.toList().sorted() }
            fun percentile(p: Double): Double = samples[(ceil(samples.size * p).toInt() - 1)
                .coerceIn(samples.indices)] / 1_000_000.0
            val firstVisibleMs = (firstChangedPreDrawNs.get() - firstMoveNs.get()) / 1_000_000.0
            assertTrue("The drag must produce a changed rendered layout", firstVisibleMs >= 0)
            val seconds = (finishedNs.get() - firstMoveNs.get()) / 1_000_000_000.0
            val fps = samples.size / seconds
            val jankCount = samples.count { it > 16_600_000L }
            Log.i("WidgetResizeFrameProbe", "A7 moves=120 frames=${samples.size} seconds=$seconds fps=$fps jankCount=$jankCount " +
                "p50TotalMs=${percentile(.50)} p95TotalMs=${percentile(.95)} " +
                "maxTotalMs=${samples.last() / 1_000_000.0} " +
                "firstChangedPreDrawLatencyMs=$firstVisibleMs " +
                "droppedMetricReports=${droppedReports.get()} density=$density " +
                "displayPx=${device.displayWidth}x${device.displayHeight}")
            val phaseSamples = synchronized(phaseDurations) { phaseDurations.toList() }
            Log.i("WidgetResizeFrameProbe", "A7 phaseMeanMs unknown,input,animation,layout,draw,sync,command,swap=" +
                phases.indices.map { index -> phaseSamples.map { it[index] }.average() / 1_000_000.0 })
            // PLAN4's explicit emulator budgets, not a promise about all physical devices.
            // First changed pre-draw is reported honestly; recording verifies presentation.
            assertTrue("A7 requires zero frames over16.6ms; observed $jankCount", jankCount == 0)
            assertTrue("Whole-build resize must sustain55fps; observed $fps", fps >= 55.0)
            assertTrue("A7 measurement must span approximately2seconds; observed $seconds", seconds in 1.9..2.2)
            assertTrue("Missing frame reports cannot substantiate zerojank", droppedReports.get() == 0)
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
