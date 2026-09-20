// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCommitCorrelationTest {
    @Test fun delayedUnchangedFrameCommitCannotBorrowLaterChangedGeometry() {
        val original = ObservedFrameBounds(0, 10, 100, 190)
        val earlier = FrameCommitCorrelation(1, 100)
        earlier.observeDraw(ObservedFrameDraw(110, original))
        val later = FrameCommitCorrelation(2, 120)
        later.observeDraw(ObservedFrameDraw(130, original.copy(bottom = 200)))
        // The older callback arrives only after the new bounds have already been drawn.
        earlier.observeCommit(140)
        later.observeCommit(150)
        assertTrue(earlier.isPaired())
        assertFalse(earlier.containsChangedBounds(original))
        assertTrue(later.containsChangedBounds(original))
    }

    @Test fun missingOrDuplicateDrawCannotClaimAChangedFrameCommit() {
        val original = ObservedFrameBounds(0, 10, 100, 190)
        val frame = FrameCommitCorrelation(1, 100)
        frame.observeCommit(140)
        assertFalse(frame.containsChangedBounds(original))
        frame.observeDraw(ObservedFrameDraw(110, original.copy(bottom = 200)))
        frame.observeDraw(ObservedFrameDraw(120, original.copy(bottom = 220)))
        assertFalse(frame.isPaired())
        assertFalse(frame.containsChangedBounds(original))
    }
}
