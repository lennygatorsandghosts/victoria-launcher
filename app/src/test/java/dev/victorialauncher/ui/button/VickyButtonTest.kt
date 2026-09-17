// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import dev.victorialauncher.data.ButtonSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VickyButtonTest {
    @Test
    fun `classifySwipe ignores movement at or under the threshold`() {
        assertNull(classifySwipe(48f, 0f, 48f))
        assertNull(classifySwipe(0f, -48f, 48f))
        assertNull(classifySwipe(24f, -24f, 48f))
    }

    @Test
    fun `classifySwipe uses the dominant axis`() {
        assertEquals(ButtonSlot.SWIPE_UP, classifySwipe(30f, -70f, 48f))
        assertEquals(ButtonSlot.SWIPE_LEFT, classifySwipe(-70f, -30f, 48f))
        assertEquals(ButtonSlot.SWIPE_RIGHT, classifySwipe(70f, -30f, 48f))
    }

    @Test
    fun `classifySwipe ignores downward swipes`() {
        assertNull(classifySwipe(0f, 70f, 48f))
        assertNull(classifySwipe(40f, 70f, 48f))
    }
}
