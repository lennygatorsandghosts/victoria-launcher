// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import dev.victorialauncher.HostedWidgetFixture.Companion.TOP_HANDLE
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Black-box resize acceptance checks. Missing future controls fail assertions, not compilation. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeTest {
    private val fixture = HostedWidgetFixture()

    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun a1_bottomDragTracksFingerAndPersists100Dp() = with(fixture) {
        enterResize()
        val before = bounds()
        val initialHeight = heightPref()
        drag(BOTTOM_HANDLE, 100f) { index, sentDp ->
            if (index == 24) {
                await("bottom tracks actual intermediate displacement $sentDp dp") {
                    kotlin.math.abs((bounds().bottom - before.bottom) / density - sentDp) <= 2f
                }
                assertDp("Top edge stays fixed during bottom drag", before.top / density, bounds().top / density)
            }
        }
        await("height persisted after bottom release") { heightPref() == initialHeight + 100 }
        assertDp("Bottom rendered edge moves 100dp", 100f, (bounds().bottom - before.bottom) / density)
        assertDp("Stored height grows 100dp", 100f, (heightPref() - initialHeight).toFloat())
        assertDp("Top stays fixed after release", before.top / density, bounds().top / density)
    }

    @Test fun a2_topDragMovesTopAndPreservesRenderedBottom() = with(fixture) {
        enterResize()
        val before = bounds()
        val initialHeight = heightPref()
        val initialTop = topPref()
        drag(TOP_HANDLE, -60f) { index, sentDp ->
            if (index == 24) {
                await("top tracks actual intermediate displacement $sentDp dp") {
                    kotlin.math.abs((bounds().top - before.top) / density - sentDp) <= 2f
                }
                assertDp("Bottom is stationary while dragging top", before.bottom / density, bounds().bottom / density)
            }
        }
        await("height and top padding persisted together") {
            heightPref() == initialHeight + 60 && topPref() == initialTop - 60
        }
        assertDp("Stored height grows 60dp", 60f, (heightPref() - initialHeight).toFloat())
        assertDp("Stored padding shrinks 60dp", -60f, (topPref() - initialTop).toFloat())
        assertDp("Top rendered edge moves up 60dp", -60f, (bounds().top - before.top) / density)
        assertDp("Bottom stays fixed after release", before.bottom / density, bounds().bottom / density)
    }
}
