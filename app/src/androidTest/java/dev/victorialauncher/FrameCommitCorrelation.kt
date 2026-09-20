// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

/** Immutable geometry captured after the window's children have drawn. */
internal data class ObservedFrameBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class ObservedFrameDraw(val timestampNs: Long, val bounds: ObservedFrameBounds)

/** A delayed commit owns this traversal's observation, never a later traversal's geometry. */
internal class FrameCommitCorrelation(val sequence: Int, val preDrawNs: Long) {
    var drawCalls = 0
        private set
    var commitCalls = 0
        private set
    var observation: ObservedFrameDraw? = null
        private set
    var commitNs = 0L
        private set

    fun observeDraw(value: ObservedFrameDraw) {
        drawCalls++
        if (drawCalls == 1) observation = value
    }

    fun observeCommit(timestampNs: Long) {
        commitCalls++
        if (commitCalls == 1) commitNs = timestampNs
    }

    fun isPaired(): Boolean = drawCalls == 1 && commitCalls == 1 &&
        observation?.let { preDrawNs <= it.timestampNs && it.timestampNs <= commitNs } == true

    fun containsChangedBounds(original: ObservedFrameBounds): Boolean =
        isPaired() && observation?.bounds != original
}
