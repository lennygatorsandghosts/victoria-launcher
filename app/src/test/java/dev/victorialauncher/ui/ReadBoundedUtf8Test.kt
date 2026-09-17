// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch

class ReadBoundedUtf8Test {

    /**
     * Behaves the way a SAF provider that neither writes nor closes would: [read] never
     * returns. [readBoundedUtf8]'s own byte cap can't fire on this, since it only checks a
     * total that a blocked read never adds to (SEC-L8) -- this is exactly the case the
     * production caller in `VictoriaNavHost` guards against with `runInterruptible` inside
     * `withTimeoutOrNull`.
     */
    private class BlockingInputStream : InputStream() {
        private val neverSignals = CountDownLatch(1)
        override fun read(): Int {
            neverSignals.await()
            return -1
        }
    }

    @Test(timeout = 5_000)
    fun `a stream that never yields a byte is abandoned rather than blocking forever`() {
        val result = runBlocking {
            withTimeoutOrNull(200) {
                runInterruptible(Dispatchers.IO) { readBoundedUtf8(BlockingInputStream(), 1024) }
            }
        }
        assertNull(result)
    }

    @Test
    fun `a well-behaved stream under the cap still reads normally`() {
        val text = "hello"
        val result = runBlocking {
            withTimeoutOrNull(5_000) {
                runInterruptible(Dispatchers.IO) {
                    readBoundedUtf8(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), 1024)
                }
            }
        }
        assertEquals(text, result)
    }
}
