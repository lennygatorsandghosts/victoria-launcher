// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Instrumentation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.data.SearchUrl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box: configures a search template, opens it from the A-Z list, types a query with a
 * space and an ampersand, submits, and checks the VIEW intent that actually fired carried the
 * exact URL [SearchUrl.build] would produce for it.
 *
 * Capturing the intent: the search dialog's `onSubmit` calls `context.startActivity(...)` on
 * Compose's `LocalContext`, which for a screen composed inside [MainActivity] is the Activity
 * itself — so the call goes through `Activity.startActivityForResult`, which is what actually
 * consults [Instrumentation]'s registered monitors (a plain, non-Activity Context does not).
 * An instrumented test's target-package app runs inside this test's own process — that is what
 * "android:targetPackage" self-instrumentation means, and it is why [LauncherTestUtils] can
 * already reach [MainActivity] directly (see its doc comment) — so a monitor registered here
 * sees that same call.
 *
 * A stock `ActivityMonitor` only ever hands back an `Activity` instance, which blocking mode
 * never produces (the real Activity is never created) and which would not expose the Intent's
 * contents anyway. [CapturingMonitor] overrides `onStartActivity` instead: returning a non-null
 * `ActivityResult` from it short-circuits the launch immediately, before any real browser is
 * resolved, and hands the exact [Intent] straight to the test.
 *
 * That override is only ever consulted for a monitor built with the no-arg `ActivityMonitor()`
 * constructor — `Instrumentation.execStartActivity` only calls `onStartActivity` when
 * `ignoreMatchingSpecificIntents()` is set, which is exactly what that constructor (and only
 * that one) turns on; the filter-based constructor relies purely on `IntentFilter` matching
 * and never calls the override at all, so a filter passed there would be silently ignored and
 * every matching start would be blocked with nothing ever captured. [CapturingMonitor] filters
 * by hand in the override instead, and returns null for anything else so every other activity
 * start on the device — including the launcher's own — proceeds exactly as it would with no
 * monitor installed.
 */
@RunWith(AndroidJUnit4::class)
class SearchEntryTest {

    private class CapturingMonitor : Instrumentation.ActivityMonitor() {
        @Volatile var captured: Intent? = null

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW) return null
            if (intent.data?.scheme?.lowercase() !in SCHEMES) return null
            captured = intent
            return Instrumentation.ActivityResult(0, null)
        }

        private companion object {
            val SCHEMES = setOf("http", "https")
        }
    }

    private lateinit var instrumentation: Instrumentation
    private lateinit var prefs: Prefs

    private val template = "https://search.example.org/search?q=%s"

    // Not the default "Search": the dialog's own button says that too, and a label found
    // nowhere else on screen is what makes finding the row mean something.
    private val rowLabel = "Web lookup"

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        // The same underlying DataStore the running app reads: preferencesDataStore() caches
        // itself per applicationContext, so a second Prefs wrapping the same target context is
        // not a second store — it is how the app is meant to be reached from outside its own UI.
        prefs = Prefs(instrumentation.targetContext.applicationContext)
        runBlocking {
            prefs.setSearchUrlTemplate(template)
            prefs.setSearchLabel(rowLabel)
        }
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    @After
    fun tearDown() {
        runBlocking {
            prefs.setSearchUrlTemplate("")
            prefs.setSearchLabel("")
        }
    }

    // Instrumented test names become DEX method names, which can't contain spaces — camelCase,
    // matching LauncherSmokeTest rather than the backtick style used under app/src/test.
    @Test
    fun submittingAQueryFiresTheBuiltSearchUrl() {
        val query = "cat & dog"
        val expectedUrl = SearchUrl.build(template, query)
        assertNotNull("the fixture template/query should themselves build a URL", expectedUrl)

        val monitor = CapturingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            val device = LauncherTestUtils.uiDevice()

            LauncherTestUtils.openAppList()
            // The list only composes the rows on screen, and how many sit above this one
            // depends on what else is installed or pinned. Filtering by name is how a user
            // would reach it too, and it brings the row into view wherever it sorts.
            // Part of the name only, so the box never holds the text looked for next.
            LauncherTestUtils.filterAppList(rowLabel.dropLast(3))
            assertTrue(
                "expected the search row in the A-Z list",
                LauncherTestUtils.waitForText(rowLabel),
            )
            device.findObject(By.text(rowLabel)).click()

            // The list has a text field of its own, so the dialog is recognised by its hint
            // rather than by being a text field.
            assertTrue(
                "expected the search dialog to appear",
                LauncherTestUtils.waitForText("Search the web", 5_000L),
            )
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field.text = query

            // The field's IME action is Search (singleLine, ImeAction.Search); a hardware
            // Enter is delivered to the focused field the same way a soft keyboard's search
            // key would be, and triggers the same onSearch callback.
            device.pressEnter()

            val deadline = System.currentTimeMillis() + 10_000L
            while (monitor.captured == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }

            val firedIntent = monitor.captured
            assertNotNull("expected the search dialog to have started a VIEW activity", firedIntent)
            assertEquals(Intent.ACTION_VIEW, firedIntent!!.action)
            assertEquals(expectedUrl, firedIntent.dataString)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
