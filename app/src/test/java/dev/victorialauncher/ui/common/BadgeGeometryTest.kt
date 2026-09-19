package dev.victorialauncher.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeGeometryTest {

    @Test
    fun `192px icon places a 40 percent badge flush bottom right with halo`() {
        assertEquals(
            BadgeGeometry(
                left = 115,
                top = 115,
                size = 77,
                ring = 6,
                haloLeft = 109,
                haloTop = 109,
                haloSize = 89,
            ),
            badgeGeometry(192),
        )
    }

    @Test
    fun `48px icon keeps at least a 1px ring`() {
        val geometry = badgeGeometry(48)
        assertNotNull(geometry)

        assertEquals(29, geometry!!.left)
        assertEquals(29, geometry.top)
        assertEquals(19, geometry.size)
        assertEquals(1, geometry.ring)
        assertEquals(28, geometry.haloLeft)
        assertEquals(28, geometry.haloTop)
        assertEquals(21, geometry.haloSize)
    }

    @Test
    fun `badges smaller than 8px are omitted`() {
        assertNull(badgeGeometry(16))
    }

    @Test
    fun `8px badge is still drawn`() {
        val geometry = badgeGeometry(20)
        assertNotNull(geometry)

        assertEquals(12, geometry!!.left)
        assertEquals(12, geometry.top)
        assertEquals(8, geometry.size)
    }

    @Test
    fun `ratio must be greater than zero and less than one`() {
        assertThrows(IllegalArgumentException::class.java) {
            badgeGeometry(192, ratio = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            badgeGeometry(192, ratio = 1f)
        }
    }

    @Test
    fun `badge rect stays inside the icon`() {
        for (px in 20..512) {
            val geometry = badgeGeometry(px) ?: continue

            assertTrue("left should be inside for $px", geometry.left >= 0)
            assertTrue("top should be inside for $px", geometry.top >= 0)
            assertTrue("right should be inside for $px", geometry.left + geometry.size <= px)
            assertTrue("bottom should be inside for $px", geometry.top + geometry.size <= px)
        }
    }

    @Test
    fun `badge cache key suffix is only present for enabled shortcut badges`() {
        assertEquals("", badgeKeySuffix(isShortcut = false, enabled = true, overridesStamp = 42))
        assertEquals("", badgeKeySuffix(isShortcut = true, enabled = false, overridesStamp = 42))
        assertEquals("|b1|42", badgeKeySuffix(isShortcut = true, enabled = true, overridesStamp = 42))
    }
}
