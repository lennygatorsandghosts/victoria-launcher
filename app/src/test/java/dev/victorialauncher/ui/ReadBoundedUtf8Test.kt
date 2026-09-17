// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch

class ReadBoundedUtf8Test {

    /**
     * Behaves the way a real, stalled SAF pipe read does: [read] blocks, and it does not
     * unblock on a thread interrupt -- looping on [CountDownLatch.await] and swallowing the
     * [InterruptedException] it throws reproduces that, since a genuine blocking native read
     * ignores the interrupt the same way. The only thing that ever returns it is [close].
     */
    private class UninterruptibleBlockingInputStream : InputStream() {
        private val closedLatch = CountDownLatch(1)
        var closeCount = 0
            private set

        override fun read(): Int {
            while (closedLatch.count > 0) {
                try {
                    closedLatch.await()
                } catch (_: InterruptedException) {
                    // Ignored on purpose -- this is exactly what makes a real blocked pipe
                    // read immune to Thread.interrupt(), and so to runInterruptible/coroutine
                    // cancellation, which only ever deliver an interrupt.
                }
            }
            throw IOException("closed")
        }

        override fun close() {
            closedLatch.countDown()
            closeCount++
        }
    }

    /**
     * The case a stalled SAF provider actually produces. A plain `withTimeoutOrNull` around a
     * `runInterruptible` read of this stream never returns -- proven directly: the equivalent
     * of that old shape, run against this same stream, fails with a JUnit timeout rather than
     * completing (`TestTimedOutException`, reproduced while writing this test). Only closing
     * the stream out from under the blocked read unblocks it, which is what
     * [readBoundedUtf8WithTimeout] does once its own timeout elapses.
     */
    @Test(timeout = 5_000)
    fun `a read that ignores interrupts is unblocked by closing the stream, not by cancellation alone`() {
        val stream = UninterruptibleBlockingInputStream()
        val result = runBlocking { readBoundedUtf8WithTimeout(stream, 1024, 200) }
        assertNull(result)
        assertTrue("the stream should have been closed to unblock the read", stream.closeCount >= 1)
    }

    @Test
    fun `a well-behaved stream under the cap still reads normally`() {
        val text = "hello"
        val result = runBlocking {
            readBoundedUtf8WithTimeout(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), 1024, 5_000)
        }
        assertEquals(text, result)
    }

    @Test
    fun `an oversize stream is reported as null, the same as a timeout`() {
        val text = "hello world"
        val result = runBlocking {
            readBoundedUtf8WithTimeout(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), 4, 5_000)
        }
        assertNull(result)
    }
}
