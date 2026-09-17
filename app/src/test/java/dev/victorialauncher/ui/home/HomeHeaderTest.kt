// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeHeaderTest {

    private val date = LocalDate.of(2025, 2, 3)

    @Test
    fun `header text formats a fixed date in US order`() {
        assertEquals("Mon, Feb 3  87%", headerText(date, Locale.US, 87, "EEE, MMM d"))
    }

    @Test
    fun `header text formats a fixed date in UK order`() {
        assertEquals("Mon, 3 Feb  87%", headerText(date, Locale.UK, 87, "EEE, d MMM"))
    }

    @Test
    fun `battery percent uses level and scale`() {
        assertEquals(50, batteryPercent(level = 25, scale = 50))
    }

    @Test
    fun `battery percent is null when scale is zero`() {
        assertNull(batteryPercent(level = 25, scale = 0))
    }

    @Test
    fun `battery percent is null when level is negative`() {
        assertNull(batteryPercent(level = -1, scale = 100))
    }
}
