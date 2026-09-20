// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetResizeLimitsTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun a3_bottomCannotShrinkBelow80Dp() = with(fixture) {
        enterResize()
        val before = bounds()
        drag(BOTTOM_HANDLE, -150f)
        await("minimum 80dp persisted") { heightPref() == 80 }
        assertDp("Rendered minimum is 80dp", 80f, bounds().height() / density)
        assertDp("Minimum clamp preserves top edge", before.top / density, bounds().top / density)
    }

    /** Run separately on a synthetic display at least 1200dp tall. No assumptions/skips. */
    @Test fun a3_bottomCannotGrowBeyond900Dp_requires1200DpDisplay() = with(fixture) {
        assertTrue("A3 maximum needs a synthetic display >=1200dp tall; " +
            "a phone's safe-area clamp cannot prove the independent 900dp cap",
            device.displayHeight / density >= 1200f)
        enterResize()
        val before = bounds()
        assertTrue("Synthetic display must leave room to inject the entire 850dp gesture",
            before.bottom + 850f * density < device.displayHeight - 24f * density)
        drag(BOTTOM_HANDLE, 850f, moves = 60)
        await("maximum 900dp persisted") { heightPref() == 900 }
        assertDp("Rendered maximum is 900dp", 900f, bounds().height() / density)
        assertDp("Maximum clamp preserves top edge", before.top / density, bounds().top / density)
    }
}
