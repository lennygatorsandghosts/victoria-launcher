// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

/** A7 measurement probe. Performance acceptance is reviewed from logs on a named device. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeFrameProbeTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    /** Measures newly added layout/traversal/commit timestamps. The strict A7 gate remains
     * unresolved and is tested separately; this diagnostic is not an acceptance result. */
    @Test fun diagnostic_composeLayoutAndCommitTiming(): Unit = with(fixture) {
        enterResize()
        measureDrag("DIAGNOSTIC_COMPOSE_LATENCY", { hostOnMain() }, correlateTraversals = true) { first, pastSlop ->
            dragForDuration(BOTTOM_HANDLE, 100f, beforeFirstMove = first, beforePastSlopMove = pastSlop)
        }
        Log.i("WidgetResizeFrameProbe", "DIAGNOSTIC_COMPOSE_LATENCY acceptanceClaim=false strictA7GateUnresolved=true")
    }

    @Test fun a7_reportsWindowFrameTimesAndFirstVisibleMovementLatency(): Unit = with(fixture) {
        enterResize()
        val result = measureDrag("A7", { hostOnMain() }, correlateTraversals = true) { first, pastSlop ->
            dragForDuration(BOTTOM_HANDLE, 100f, beforeFirstMove = first, beforePastSlopMove = pastSlop)
        }
        val (jankCount, fps, seconds, droppedReports, firstChangedFrameCommitMs) = result
        // PLAN4's explicit emulator budgets, not a promise about all physical devices.
        // Compose can lay out during dispatchDraw, after tree pre-draw. Gate the callback
        // correlated with the first changed rendered frame; recording verifies presentation.
        assertTrue("A7 requires zero frames over16.6ms; observed $jankCount", jankCount == 0)
        assertTrue("Whole-build resize must sustain55fps; observed $fps", fps >= 55.0)
        assertTrue("A7 measurement must span approximately2seconds; observed $seconds", seconds in 1.9..2.2)
        assertTrue("Missing frame reports cannot substantiate zerojank", droppedReports == 0)
        assertTrue("A7 first changed rendered frame must be submitted in under2frames; " +
            "observed ${firstChangedFrameCommitMs}ms",
            firstChangedFrameCommitMs != null && firstChangedFrameCommitMs < 33.2)
    }

    /** Diagnostic only: real touch resizes a plain View in the same hardware-rendered window.
     * This is a pipeline floor, not an equivalent replacement for Compose/widget acceptance. */
    @Test fun calibration_plainViewDragReportsSameWindowPipeline(): Unit = with(fixture) {
        val original = bounds()
        lateinit var parent: ViewGroup
        lateinit var surface: FrameLayout
        lateinit var block: View
        val movesReceived = AtomicInteger()
        instrumentation.runOnMainSync {
            parent = activityOnMain().window.decorView as ViewGroup
            surface = FrameLayout(activityOnMain()).apply { setBackgroundColor(Color.DKGRAY) }
            block = View(activityOnMain()).apply { setBackgroundColor(Color.CYAN) }
            val origin = IntArray(2)
            parent.getLocationOnScreen(origin)
            surface.addView(block, FrameLayout.LayoutParams(original.width(), original.height()).apply {
                leftMargin = original.left - origin[0]
                topMargin = original.top - origin[1]
            })
            var downY = 0f
            var downHeight = 0
            surface.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = event.rawY; downHeight = block.height }
                    MotionEvent.ACTION_MOVE -> {
                        movesReceived.incrementAndGet()
                        block.layoutParams = block.layoutParams.apply {
                            height = downHeight + (event.rawY - downY).roundToInt()
                        }
                    }
                }
                true
            }
            parent.addView(surface, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        }
        try {
            await("Plain reference View has the original rendered bounds") {
                var ready = false
                instrumentation.runOnMainSync { ready = block.height == original.height() && block.isShown }
                ready
            }
            measureDrag("REFERENCE_PLAIN_VIEW", { block }, correlateTraversals = true) { first, pastSlop ->
                dragForDuration(original.exactCenterX(), original.bottom - 2 * density, 100f,
                    beforeFirstMove = first, beforePastSlopMove = pastSlop)
            }
            var finalHeight = 0
            instrumentation.runOnMainSync { finalHeight = block.height }
            assertDp("Reference's actual rendered height follows the injected 100dp drag",
                100f, (finalHeight - original.height()) / density)
            assertTrue("Reference must receive real injected MOVE events", movesReceived.get() > 0)
            Log.i("WidgetResizeFrameProbe", "REFERENCE_PLAIN_VIEW movesReceived=${movesReceived.get()} " +
                "renderedDeltaPx=${finalHeight - original.height()}")
        } finally {
            instrumentation.runOnMainSync { parent.removeView(surface) }
        }
    }

    private data class DragResult(val jankCount: Int, val fps: Double, val seconds: Double,
                                  val droppedReports: Int, val firstChangedFrameCommitMs: Double?)

    private fun measureDrag(label: String, measuredViewOnMain: () -> View,
                            correlateTraversals: Boolean = false,
                            drag: (() -> Unit, () -> Unit) -> Unit): DragResult = with(fixture) {
        var originalHeight = 0
        instrumentation.runOnMainSync { originalHeight = measuredViewOnMain().height }
        val firstMoveNs = AtomicLong()
        val pastSlopMoveNs = AtomicLong()
        val finishedNs = AtomicLong()
        val metricsBarrierVsyncNs = AtomicLong()
        val firstChangedPreDrawNs = AtomicLong()
        val lastPreDrawNs = AtomicLong()
        val firstChangedLayoutNs = AtomicLong()
        val preDrawBeforeFirstChangedLayoutNs = AtomicLong()
        val firstChangedPostTraversalNs = AtomicLong()
        val firstCommitAfterChangedLayoutNs = AtomicLong()
        val firstPastSlopPreDrawNs = AtomicLong()
        val changedPreDraws = AtomicInteger()
        val touchSlop = ViewConfiguration.get(app).scaledTouchSlop
        var lastHeight = originalHeight
        val totalDurationsNs = Collections.synchronizedList(mutableListOf<Long>())
        val phaseDurations = Collections.synchronizedList(mutableListOf<List<Long>>())
        val phases = listOf("unknown" to FrameMetrics.UNKNOWN_DELAY_DURATION, "input" to FrameMetrics.INPUT_HANDLING_DURATION,
            "animation" to FrameMetrics.ANIMATION_DURATION, "layout" to FrameMetrics.LAYOUT_MEASURE_DURATION,
            "draw" to FrameMetrics.DRAW_DURATION, "sync" to FrameMetrics.SYNC_DURATION,
            "command" to FrameMetrics.COMMAND_ISSUE_DURATION, "swap" to FrameMetrics.SWAP_BUFFERS_DURATION) +
            if (Build.VERSION.SDK_INT >= 31) listOf("gpu" to FrameMetrics.GPU_DURATION) else emptyList()
        val droppedReports = AtomicInteger()
        val handlerThread = HandlerThread("widget-resize-frame-probe").apply { start() }
        lateinit var window: Window
        lateinit var root: View
        lateinit var observedView: View
        val commitCallbacks = mutableListOf<Runnable>()
        val traversals = mutableListOf<FrameCommitCorrelation>()
        var activeTraversal: FrameCommitCorrelation? = null
        val overlayReady = AtomicBoolean()
        val screenLocation = IntArray(2)
        fun observedBounds(): ObservedFrameBounds {
            observedView.getLocationOnScreen(screenLocation)
            return ObservedFrameBounds(screenLocation[0], screenLocation[1],
                screenLocation[0] + observedView.width, screenLocation[1] + observedView.height)
        }
        lateinit var originalFrameBounds: ObservedFrameBounds
        val traversalObserver = object : Drawable() {
            override fun draw(canvas: Canvas) {
                // No canvas operations and no pixels. Decor's overlay is visited after its
                // children, so Compose's dispatchDraw measurement has already happened.
                overlayReady.set(true)
                activeTraversal?.observeDraw(ObservedFrameDraw(System.nanoTime(), observedBounds()))
            }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Suppress("DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSPARENT
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (firstMoveNs.get() > 0 && bottom - top != originalHeight &&
                firstChangedLayoutNs.compareAndSet(0, System.nanoTime())) {
                preDrawBeforeFirstChangedLayoutNs.set(lastPreDrawNs.get())
            }
        }
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            val height = measuredViewOnMain().height
            val observedNs = System.nanoTime()
            lastPreDrawNs.set(observedNs)
            val startedNs = firstMoveNs.get()
            val endedNs = finishedNs.get()
            activeTraversal = null
            if (correlateTraversals && startedNs > 0 && endedNs == 0L) {
                val traversal = FrameCommitCorrelation(traversals.size, observedNs)
                traversals.add(traversal)
                activeTraversal = traversal
                val callback = Runnable { traversal.observeCommit(System.nanoTime()) }
                commitCallbacks.add(callback)
                if (Build.VERSION.SDK_INT >= 29) root.viewTreeObserver.registerFrameCommitCallback(callback)
                // Dirty only within an already-running traversal, never from a timer or
                // the commit callback. Report frame counts to expose any observer overhead.
                traversalObserver.invalidateSelf()
            }
            if (firstMoveNs.get() > 0 && height != originalHeight) {
                firstChangedPreDrawNs.compareAndSet(0, observedNs)
            }
            if (pastSlopMoveNs.get() > 0 && abs(height - originalHeight) > touchSlop) {
                firstPastSlopPreDrawNs.compareAndSet(0, observedNs)
            }
            // The numerator and denominator describe the same drag interval. Release/drain
            // frames may finish a pending layout, but cannot inflate its movement rate.
            if (startedNs > 0 && observedNs >= startedNs &&
                (endedNs == 0L || observedNs <= endedNs) && height != lastHeight) {
                changedPreDraws.incrementAndGet()
            }
            lastHeight = height
            if (startedNs > 0 && firstChangedPostTraversalNs.get() == 0L) {
                // AndroidComposeView can measure/layout from dispatchDraw, after this
                // tree-wide pre-draw. Observe again once the traversal returns to the loop.
                // This is a post-traversal upper bound, not a presentation timestamp.
                root.post {
                    if (observedView.height != originalHeight) {
                        firstChangedPostTraversalNs.compareAndSet(0, System.nanoTime())
                    }
                }
            }
            if (startedNs > 0 && Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated &&
                firstCommitAfterChangedLayoutNs.get() == 0L) {
                // ViewRootImpl captures these callbacks before dispatchDraw. Registering
                // only when host layout changes during drawing would defer another frame.
                // A callback after changed layout proves submission activity, not that the
                // changed frame is already visible. Only the traversal-correlated callback
                // supplies the submitted-frame latency used for acceptance.
                val callback = Runnable {
                    if (firstChangedLayoutNs.get() > 0 && observedView.height != originalHeight) {
                        firstCommitAfterChangedLayoutNs.compareAndSet(0, System.nanoTime())
                    }
                }
                commitCallbacks.add(callback)
                root.viewTreeObserver.registerFrameCommitCallback(callback)
            }
            true
        }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            // FrameMetrics is reused by Android: copy primitive values in the callback.
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            val endedNs = finishedNs.get()
            if (firstMoveNs.get() > 0 && vsync >= firstMoveNs.get() && metricsBarrierVsyncNs.get() == 0L) {
                // Include drops reported by the first later frame: they could represent
                // missing reports from the measured interval, which cannot prove zero jank.
                droppedReports.addAndGet(dropped)
                if (endedNs > 0 && vsync > endedNs) {
                    metricsBarrierVsyncNs.set(vsync)
                } else {
                    totalDurationsNs.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                    // Keep the total and timestamps alongside phases so slow-frame attribution
                    // is correlated. TOTAL_DURATION is pipeline work, not a vsync interval.
                    val deadline = if (Build.VERSION.SDK_INT >= 31)
                        metrics.getMetric(FrameMetrics.DEADLINE) else -1L
                    phaseDurations.add(listOf(metrics.getMetric(FrameMetrics.TOTAL_DURATION), vsync,
                        metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)) +
                        phases.map { metrics.getMetric(it.second) } + deadline)
                }
            }
        }
        instrumentation.runOnMainSync {
            window = activityOnMain().window
            root = window.decorView
            observedView = measuredViewOnMain()
            originalFrameBounds = observedBounds()
            if (correlateTraversals) {
                assertTrue("Traversal/commit correlation requires hardware rendering on API29+",
                    Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated)
                traversalObserver.setBounds(0, 0, root.width, root.height)
                root.overlay.add(traversalObserver)
            }
            observedView.addOnLayoutChangeListener(layoutListener)
            root.viewTreeObserver.addOnPreDrawListener(preDraw)
            window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper))
        }
        try {
            if (correlateTraversals) {
                await("Zero-pixel observer setup has drawn before the measurement starts") { overlayReady.get() }
                instrumentation.waitForIdleSync()
            }
            drag({ firstMoveNs.set(System.nanoTime()) }, { pastSlopMoveNs.set(System.nanoTime()) })
            finishedNs.set(System.nanoTime())
            // A delivered report from a newer intended-vsync closes the ordered stream.
            // Force a frame after UP so an otherwise idle window can supply that barrier.
            instrumentation.runOnMainSync { root.postInvalidateOnAnimation() }
            await("A frame after the measurement window drains earlier frame reports") {
                metricsBarrierVsyncNs.get() > finishedNs.get()
            }
            await("First changed widget layout reaches pre-draw") { firstChangedPreDrawNs.get() > 0 }
            await("Window frame metrics arrive") { totalDurationsNs.isNotEmpty() }
            if (correlateTraversals) {
                await("Every observed traversal receives its own commit callback") {
                    var complete = false
                    instrumentation.runOnMainSync { complete = traversals.all { it.commitCalls > 0 } }
                    complete
                }
            }
            val samples = synchronized(totalDurationsNs) { totalDurationsNs.toList().sorted() }
            fun percentile(p: Double): Double = samples[(ceil(samples.size * p).toInt() - 1)
                .coerceIn(samples.indices)] / 1_000_000.0
            val firstVisibleMs = (firstChangedPreDrawNs.get() - firstMoveNs.get()) / 1_000_000.0
            var firstChangedFrameCommitMs: Double? = null
            fun sinceFirstMove(timestamp: Long): String = if (timestamp > 0)
                ((timestamp - firstMoveNs.get()) / 1_000_000.0).toString() else "unavailable"
            if (correlateTraversals) {
                instrumentation.runOnMainSync {
                    val paired = traversals.count { it.isPaired() }
                    val missingDraw = traversals.count { it.drawCalls == 0 }
                    val duplicateDraw = traversals.count { it.drawCalls > 1 }
                    val missingCommit = traversals.count { it.commitCalls == 0 }
                    val duplicateCommit = traversals.count { it.commitCalls > 1 }
                    val changed = traversals.filter { it.containsChangedBounds(originalFrameBounds) }
                    val first = changed.minByOrNull { it.commitNs }
                    firstChangedFrameCommitMs = first?.let { (it.commitNs - firstMoveNs.get()) / 1_000_000.0 }
                    Log.i("WidgetResizeFrameProbe", "$label correlationDiagnosticOnly=${label != "A7"} injectedMoves=120 " +
                        "windowMetricFrames=${samples.size} observedTraversals=${traversals.size} pairedTraversals=$paired " +
                        "missingDraw=$missingDraw duplicateDraw=$duplicateDraw missingCommit=$missingCommit " +
                        "duplicateCommit=$duplicateCommit changedBoundsPairs=${changed.size} " +
                        "firstChangedFrameDrawLatencyMs=${sinceFirstMove(first?.observation?.timestampNs ?: 0)} " +
                        "firstChangedFrameCommitCallbackLatencyMs=${sinceFirstMove(first?.commitNs ?: 0)} " +
                        "firstChangedFrameSequence=${first?.sequence} drawnBounds=${first?.observation?.bounds}")
                    assertTrue("Every observed traversal must pair exactly one draw with its own commit; " +
                        "paired=$paired total=${traversals.size}", traversals.isNotEmpty() && paired == traversals.size)
                    assertTrue("Correlation must identify an actually changed frame", first != null)
                }
            }
            val preDrawLagMs = if (firstChangedLayoutNs.get() > 0)
                ((firstChangedPreDrawNs.get() - firstChangedLayoutNs.get()) / 1_000_000.0).toString()
                else "unavailable"
            val layoutAfterPreDrawMs = if (preDrawBeforeFirstChangedLayoutNs.get() > 0)
                ((firstChangedLayoutNs.get() - preDrawBeforeFirstChangedLayoutNs.get()) / 1_000_000.0).toString()
                else "unavailable"
            Log.i("WidgetResizeFrameProbe", "$label firstHostLayoutLatencyMs=${sinceFirstMove(firstChangedLayoutNs.get())} " +
                "firstChangedPostTraversalLatencyMs=${sinceFirstMove(firstChangedPostTraversalNs.get())} " +
                "firstCommitCallbackAfterChangedLayoutLatencyMs=${sinceFirstMove(firstCommitAfterChangedLayoutNs.get())} " +
                "changedPreDrawMinusHostLayoutMs=$preDrawLagMs firstHostLayoutAfterPriorPreDrawMs=$layoutAfterPreDrawMs")
            assertTrue("The drag must produce a changed rendered layout", firstVisibleMs >= 0)
            val seconds = (finishedNs.get() - firstMoveNs.get()) / 1_000_000_000.0
            val fps = samples.size / seconds
            val jankCount = samples.count { it > 16_600_000L }
            Log.i("WidgetResizeFrameProbe", "$label moves=120 frames=${samples.size} seconds=$seconds fps=$fps jankCount=$jankCount " +
                "p50TotalMs=${percentile(.50)} p95TotalMs=${percentile(.95)} " +
                "maxTotalMs=${samples.last() / 1_000_000.0} " +
                "firstChangedPreDrawLatencyMs=$firstVisibleMs " +
                "metricsBarrierAfterEndMs=${(metricsBarrierVsyncNs.get() - finishedNs.get()) / 1_000_000.0} " +
                "droppedMetricReports=${droppedReports.get()} density=$density " +
                "displayPx=${device.displayWidth}x${device.displayHeight}")
            val phaseSamples = synchronized(phaseDurations) { phaseDurations.toList() }
            // DEADLINE is the system-allocated budget, not another rendering phase. Its
            // comparison is diagnostic and never substitutes for the fixed 16.6ms gate.
            val deadlineSamples = phaseSamples.filter { it.last() >= 0 }
            val deadlineMisses = if (deadlineSamples.isEmpty()) "unavailable" else
                deadlineSamples.count { it[0] >= it.last() }.toString()
            Log.i("WidgetResizeFrameProbe", "$label fixed16_6msFailures=$jankCount " +
                "platformDeadlineSamples=${deadlineSamples.size} platformDeadlineMisses=$deadlineMisses")
            phases.forEachIndexed { index, phase ->
                val values = phaseSamples.map { it[index + 3] }.filter { it >= 0 }.sorted()
                if (values.isNotEmpty()) {
                    fun pct(p: Double) = values[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)] / 1_000_000.0
                    Log.i("WidgetResizeFrameProbe", "$label phase=${phase.first} n=${values.size} " +
                        "meanMs=${values.average() / 1_000_000.0} p50Ms=${pct(.5)} p95Ms=${pct(.95)} maxMs=${pct(1.0)}")
                }
            }
            fun logRow(kind: String, index: Int, row: List<Long>) {
                val deadline = row.last()
                val deadlineMs = if (deadline >= 0) (deadline / 1_000_000.0).toString() else "unavailable"
                val deadlineMiss = if (deadline >= 0) (row[0] >= deadline).toString() else "unavailable"
                Log.i("WidgetResizeFrameProbe", "$label $kind=$index totalMs=${row[0] / 1_000_000.0} " +
                    "deadlineMs=$deadlineMs deadlineMiss=$deadlineMiss " +
                    "sinceFirstMoveMs=${(row[1] - firstMoveNs.get()) / 1_000_000.0} " +
                    "vsyncDelayMs=${(row[2] - row[1]) / 1_000_000.0} " +
                    phases.mapIndexed { i, phase -> "${phase.first}Ms=${row[i + 3] / 1_000_000.0}" }.joinToString(" "))
            }
            phaseSamples.take(12).forEachIndexed { index, row -> logRow("firstFrame", index, row) }
            phaseSamples.sortedByDescending { it[0] }.take(10)
                .forEachIndexed { index, row -> logRow("worstFrame", index, row) }
            val pastSlopLatency = if (firstPastSlopPreDrawNs.get() > 0)
                (firstPastSlopPreDrawNs.get() - pastSlopMoveNs.get()) / 1_000_000.0 else null
            Log.i("WidgetResizeFrameProbe", "$label changedPreDraws=${changedPreDraws.get()} " +
                "changedPreDrawsPerSecond=${changedPreDraws.get() / seconds} touchSlopPx=$touchSlop " +
                "pastSlopInjectedAtMs=${(pastSlopMoveNs.get() - firstMoveNs.get()) / 1_000_000.0} " +
                "firstPastSlopRenderedHeightLatencyMs=$pastSlopLatency")
            DragResult(jankCount, fps, seconds, droppedReports.get(), firstChangedFrameCommitMs)
        } finally {
            instrumentation.runOnMainSync {
                observedView.removeOnLayoutChangeListener(layoutListener)
                root.viewTreeObserver.removeOnPreDrawListener(preDraw)
                if (Build.VERSION.SDK_INT >= 29) {
                    commitCallbacks.forEach { root.viewTreeObserver.unregisterFrameCommitCallback(it) }
                }
                if (correlateTraversals) root.overlay.remove(traversalObserver)
                window.removeOnFrameMetricsAvailableListener(listener)
            }
            handlerThread.quitSafely()
        }
    }
}
