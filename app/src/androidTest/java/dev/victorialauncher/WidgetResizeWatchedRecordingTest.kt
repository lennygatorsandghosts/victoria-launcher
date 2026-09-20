// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import dev.victorialauncher.HostedWidgetFixture.Companion.TOP_HANDLE
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Slow, real touchscreen input for an externally captured/watched recording. OS pointer
 * indicators and video capture are configured by the operator. This is not a timing probe. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeWatchedRecordingTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun watched_bottom100DpThenTop60DpTracksRenderedEdges(): Unit = with(fixture) {
        enterResize()
        val beforeBottom = bounds()
        val initialHeight = heightPref()
        val initialTop = topPref()
        Log.i("WidgetResizeRecording", "WATCH bottom handle: drag down100dp; opposite top stays fixed")
        drag(BOTTOM_HANDLE, 100f, moves = 120, startAtRenderedEdge = true) { index, sentDp ->
            SystemClock.sleep(12)
            if (index % 12 == 0) {
                await("Recorded bottom MOVE $index reaches its sent displacement") {
                    abs((bounds().bottom - beforeBottom.bottom) / density - sentDp) <= 2f
                }
                assertDp("Recorded bottom MOVE $index keeps top fixed",
                    beforeBottom.top / density, bounds().top / density)
            }
        }
        await("Recorded bottom release persists100dp") { heightPref() == initialHeight + 100 }
        assertDp("Recorded bottom release moves actual bottom100dp", 100f,
            (bounds().bottom - beforeBottom.bottom) / density)
        assertDp("Recorded bottom release keeps top fixed", beforeBottom.top / density, bounds().top / density)
        SystemClock.sleep(350)

        val beforeTop = bounds()
        Log.i("WidgetResizeRecording", "WATCH top handle: drag up60dp; opposite bottom stays fixed")
        drag(TOP_HANDLE, -60f, moves = 120, startAtRenderedEdge = true) { index, sentDp ->
            SystemClock.sleep(12)
            if (index % 12 == 0) {
                await("Recorded top MOVE $index reaches its sent displacement") {
                    abs((bounds().top - beforeTop.top) / density - sentDp) <= 2f
                }
                assertDp("Recorded top MOVE $index keeps bottom fixed",
                    beforeTop.bottom / density, bounds().bottom / density)
            }
        }
        await("Recorded top release persists height and top together") {
            heightPref() == initialHeight + 160 && topPref() == initialTop - 60
        }
        assertDp("Recorded top release moves actual top up60dp", -60f,
            (bounds().top - beforeTop.top) / density)
        assertDp("Recorded top release keeps bottom fixed", beforeTop.bottom / density, bounds().bottom / density)
        SystemClock.sleep(350)
        Log.i("WidgetResizeRecording", "WATCH geometry assertions passed; presentation still requires watching captured video")
    }
}
