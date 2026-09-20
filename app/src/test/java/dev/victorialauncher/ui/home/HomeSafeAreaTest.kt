// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSafeAreaTest {
    @Test fun restingOriginUsesLargerStatusOrCutoutAndOptionalMargin() {
        val safe = HomeSafeArea(400f, 900f, statusTop = 31f, cutoutTop = 47f, margin = 7f)
        assertEquals(54f, safe.top, 0f)
        assertEquals(7f, safe.left, 0f)
    }

    @Test fun bottomUsesLargerNavigationOrGestureInset() {
        assertEquals(860f, HomeSafeArea(400f, 900f, navigationBottom = 40f, gestureBottom = 20f).bottom, 0f)
        assertEquals(850f, HomeSafeArea(400f, 900f, navigationBottom = 0f, gestureBottom = 50f).bottom, 0f)
    }

    @Test fun scrollAndResizeShareInsetReducedViewport() {
        val safe = HomeSafeArea(400f, 900f, statusTop = 30f, navigationBottom = 50f)
        assertEquals(820f, safe.height, 0f)
        assertEquals(-180f, safe.minOffset(1000f), 0f)
        assertEquals(0f, safe.minOffset(300f), 0f)
    }

    @Test fun horizontalOffsetUsesActualAvailableMarginAtBothExtremes() {
        val safe = HomeSafeArea(400f, 900f, insetLeft = 13f, insetRight = 17f)
        for (requested in listOf(-200f, 200f)) {
            val offset = safe.clampOffsetX(requested, 33f, 320f)
            assertTrue(33f + offset >= safe.left)
            assertTrue(33f + offset + 320f <= safe.right)
        }
        assertEquals(-20f, safe.clampOffsetX(-200f, 33f, 320f), 0f)
        assertEquals(30f, safe.clampOffsetX(200f, 33f, 320f), 0f)
    }

    @Test fun disablingSafetyRestoresFullBleed() {
        val safe = HomeSafeArea(400f, 900f, 31f, 47f, 40f, 20f, 13f, 17f, false, 7f)
        assertEquals(0f, safe.top, 0f)
        assertEquals(900f, safe.bottom, 0f)
        assertEquals(400f, safe.width, 0f)
        assertEquals(200f, safe.clampOffsetX(200f, 20f, 360f), 0f)
    }

    @Test fun liveInsetAndFoldChangesRecomputeGeometry() {
        val initial = HomeSafeArea(400f, 900f, statusTop = 31f, navigationBottom = 40f)
        val changed = initial.copy(windowWidth = 900f, windowHeight = 400f, statusTop = 0f, cutoutTop = 18f, navigationBottom = 20f)
        assertEquals(31f, initial.top, 0f)
        assertEquals(18f, changed.top, 0f)
        assertEquals(362f, changed.height, 0f)
        assertEquals(900f, changed.width, 0f)
    }

    @Test fun insetsCannotProduceNegativeViewport() {
        val safe = HomeSafeArea(20f, 20f, statusTop = 30f, navigationBottom = 40f, insetLeft = 30f, insetRight = 30f)
        assertTrue(safe.width >= 0f)
        assertTrue(safe.height >= 0f)
        assertTrue(safe.top <= safe.bottom)
    }
}
