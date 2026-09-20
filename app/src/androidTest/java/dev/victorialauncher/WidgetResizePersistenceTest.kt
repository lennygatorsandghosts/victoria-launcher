// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.os.FileObserver
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WidgetResizePersistenceTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    @Test fun a4_thirtySixMovesCommitExactlyOneActualDataStoreFileWrite() = with(fixture) {
        // Watch the transaction's on-disk atomic rename, not an emission, setter call, or
        // resize-only self-report. A calibration write proves this observer is operational.
        // Other preferences stores in this same directory count as writes too.
        val directory = File(app.filesDir, "datastore")
        assertTrue("Real DataStore directory must exist", directory.isDirectory)
        val commits = AtomicInteger()
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(directory.absolutePath, FileObserver.MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (event and FileObserver.MOVED_TO != 0 && path?.endsWith(".preferences_pb") == true) {
                    commits.incrementAndGet()
                }
            }
        }
        observer.startWatching()
        try {
            val initialHeight = heightPref()
            runBlocking { app.prefs.setWidgetHeightDp(initialHeight + 1) }
            await("DataStore observer calibration write") { commits.get() >= 1 }
            assertEquals("One calibration transaction must produce one observed commit", 1, commits.get())
            runBlocking { app.prefs.setWidgetHeightDp(initialHeight) }
            await("DataStore observer calibration restore") { commits.get() >= 2 }
            SystemClock.sleep(150)
            assertEquals("Two calibration transactions must produce two observed commits", 2, commits.get())
            enterResize()
            val beforeDrag = commits.get()
            drag(BOTTOM_HANDLE, 100f, moves = 36) { index, _ ->
                if (index == 36) {
                    // UP has not been injected. Give any erroneously enqueued writes time
                    // to reach disk so a fast release cannot hide per-MOVE persistence.
                    SystemClock.sleep(200)
                    assertEquals("No DataStore commit is allowed while finger remains down",
                        beforeDrag, commits.get())
                    assertEquals("Persistent height remains unchanged until release", initialHeight, heightPref())
                }
            }
            await("Final height persisted") { heightPref() == initialHeight + 100 }
            await("One actual disk commit after UP") { commits.get() > beforeDrag }
            SystemClock.sleep(250)
            assertEquals("36 MOVE events and UP must cause exactly one actual DataStore commit",
                1, commits.get() - beforeDrag)
        } finally {
            observer.stopWatching()
        }
    }
}
