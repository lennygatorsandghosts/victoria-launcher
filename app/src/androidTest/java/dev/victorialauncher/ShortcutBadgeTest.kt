package dev.victorialauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconShape
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.ui.common.IconStyle
import dev.victorialauncher.ui.common.clearIconCache
import dev.victorialauncher.ui.common.warmIconCache
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutBadgeTest {

    private lateinit var device: UiDevice
    private lateinit var targetContext: Context
    private lateinit var prefs: Prefs
    private lateinit var originalPrefs: String

    private val pinnedIds = mutableListOf<String>()

    @Before
    fun setUp() = runBlocking {
        device = LauncherTestUtils.uiDevice()
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = Prefs(targetContext.applicationContext)
        // The fork's export takes a stripping flag and returns a result object; a test
        // restoring its own device must keep every profile's keys.
        originalPrefs = prefs.exportJson(stripOtherProfiles = false).json

        assumeTestPackInstalled()
        LauncherTestUtils.setAsDefaultHome()
        clearPinnedShortcuts()
        cleanBadgeTestPrefs()
        LauncherTestUtils.goHome()
    }

    @After
    fun tearDown() = runBlocking {
        if (::device.isInitialized) {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
        if (::targetContext.isInitialized) {
            clearPinnedShortcuts()
        }
        if (::prefs.isInitialized && ::originalPrefs.isInitialized) {
            prefs.importJson(originalPrefs)
        }
        clearIconCache()
    }

    @Test
    fun shortcutShowsParentAppBadgeWithoutIconPack() {
        val bounds = showShortcutOnHome("probe_0", badgeEnabled = true)
        val screenshot = takeScreenshot()

        assertApproxColor("shortcut icon", averageColor(screenshot, bounds, 0.10f, 0.30f), CYAN)
        assertApproxColor("parent badge", averageColor(screenshot, bounds, 0.72f, 0.90f), MAGENTA)
    }

    @Test
    fun shortcutParentBadgeUsesSelectedIconPack() {
        val bounds = showShortcutOnHome("probe_0", badgeEnabled = true) {
            prefs.setIconPackPackage(TEST_PACKAGE)
        }
        val screenshot = takeScreenshot()

        assertApproxColor("shortcut icon", averageColor(screenshot, bounds, 0.10f, 0.30f), CYAN)
        assertApproxColor("icon-pack parent badge", averageColor(screenshot, bounds, 0.72f, 0.90f), YELLOW)
    }

    @Test
    fun shortcutRowIconOverrideDoesNotReplaceParentBadge() {
        val bounds = showShortcutOnHome("probe_0", badgeEnabled = true) { key ->
            prefs.setIconOverride(key, "pack:$TEST_PACKAGE:tile_green")
        }
        val screenshot = takeScreenshot()

        assertApproxColor("shortcut override icon", averageColor(screenshot, bounds, 0.10f, 0.30f), GREEN)
        assertApproxColor("parent badge", averageColor(screenshot, bounds, 0.72f, 0.90f), MAGENTA)
    }

    @Test
    fun shortcutBadgeToggleOffLeavesShortcutIconUnbadged() {
        val bounds = showShortcutOnHome("probe_0", badgeEnabled = false)
        val screenshot = takeScreenshot()

        assertApproxColor("shortcut icon", averageColor(screenshot, bounds, 0.10f, 0.30f), CYAN)
        assertApproxColor("unbadged shortcut corner", averageColor(screenshot, bounds, 0.72f, 0.90f), CYAN)
    }

    @Test
    fun warmingShortcutBadgeCacheStaysWithinBudget() = runBlocking {
        val shortcutIds = (0 until 20).map { "probe_$it" }
        pinShortcuts(shortcutIds)

        val rows = (0 until 20).map { shortcutRow(it) } + parentAppRows()
        // One discarded warm-up of each, then off/on alternated. Measured before the badge
        // existed: all-off-then-all-on timed IDENTICAL code at 19.3 ms vs 28.3 ms (+46%), so
        // the order, not the feature, decided the result. Alternating removes that bias.
        timeWarmIconCache(rows, badgeEnabled = false)
        timeWarmIconCache(rows, badgeEnabled = true)
        val offRuns = mutableListOf<Long>()
        val onRuns = mutableListOf<Long>()
        repeat(PERF_RUNS) {
            offRuns += timeWarmIconCache(rows, badgeEnabled = false)
            onRuns += timeWarmIconCache(rows, badgeEnabled = true)
        }
        val medianOff = offRuns.median()
        val medianOn = onRuns.median()

        Log.i(TAG, "BadgePerf medianOff=${medianOff}ns medianOn=${medianOn}ns")
        assertTrue(
            "warming shortcut badges should be within 25%; off=${medianOff}ns on=${medianOn}ns",
            medianOn <= medianOff * 1.25,
        )
    }

    private fun assumeTestPackInstalled() {
        val installed = runCatching {
            targetContext.packageManager.getPackageInfo(TEST_PACKAGE, 0)
        }.isSuccess
        val reason = "Skipping shortcut badge tests: $TEST_PACKAGE is not installed on this device"
        if (!installed) {
            Log.i(TAG, reason)
            println(reason)
        }
        assumeTrue(reason, installed)
    }

    private suspend fun cleanBadgeTestPrefs() {
        prefs.setFavorites(emptyList())
        prefs.setIconPackPackage(null)
        prefs.setIconSizeDp(96)
        prefs.setShowAppIcons(true)
        prefs.setShowFavoriteLabels(true)
        prefs.setAlignment(HomeAlignment.LEFT)
        prefs.setIconSide(IconSide.LEFT)
        prefs.setThemedIcons(false)
        prefs.setIconShape(IconShape.SYSTEM)
        TEST_SHORTCUT_IDS.forEach { id ->
            prefs.forgetEntry(shortcutKey(id))
        }
        setShortcutBadgePref(true)
    }

    private fun showShortcutOnHome(
        shortcutId: String,
        badgeEnabled: Boolean,
        configure: suspend (String) -> Unit = {},
    ): Rect {
        val key = shortcutKey(shortcutId)
        runBlocking {
            cleanBadgeTestPrefs()
            setShortcutBadgePref(badgeEnabled)
            configure(key)
        }
        pinShortcuts(listOf(shortcutId))
        runBlocking {
            prefs.addFavorite(key)
        }
        clearIconCache()
        LauncherTestUtils.goHome()

        val label = shortcutId.removePrefix("probe_").toIntOrNull()?.let { "Probe $it" } ?: shortcutId
        val icon = device.wait(Until.findObject(By.desc(label)), WAIT_MS)
            ?: error("expected shortcut icon with content description \"$label\"")
        val bounds = icon.visibleBounds
        assertTrue("expected shortcut icon to have visible bounds", bounds.width() > 0 && bounds.height() > 0)
        return bounds
    }

    private fun pinShortcuts(ids: List<String>) {
        val launcherApps = targetContext.getSystemService(LauncherApps::class.java)
        launcherApps.pinShortcuts(TEST_PACKAGE, ids, Process.myUserHandle())
        pinnedIds.clear()
        pinnedIds += ids
        waitUntilPinned(ids)
    }

    private fun waitUntilPinned(ids: List<String>) {
        val expected = ids.toSet()
        val launcherApps = targetContext.getSystemService(LauncherApps::class.java)
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(TEST_PACKAGE)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val pinned = runCatching {
                launcherApps.getShortcuts(query, Process.myUserHandle())
            }.getOrNull().orEmpty().map { it.id }.toSet()
            if (pinned.containsAll(expected)) return
            SystemClock.sleep(100)
        }
        error("expected pinned shortcuts $expected for $TEST_PACKAGE")
    }

    private fun clearPinnedShortcuts() {
        runCatching {
            targetContext.getSystemService(LauncherApps::class.java)
                .pinShortcuts(TEST_PACKAGE, emptyList(), Process.myUserHandle())
        }
        pinnedIds.clear()
    }

    private suspend fun setShortcutBadgePref(enabled: Boolean) {
        val root = JSONObject(prefs.exportJson(stripOtherProfiles = false).json)
        val values = root.getJSONObject("values")
        values.put(
            SHORTCUT_APP_BADGE_PREF,
            JSONObject()
                .put("type", "boolean")
                .put("value", enabled),
        )
        assertTrue(
            "expected Prefs.importJson to accept the shortcut badge preference",
            prefs.importJson(root.toString()),
        )
    }

    private fun takeScreenshot(): Bitmap {
        val file = File(targetContext.cacheDir, "shortcut-badge-${SystemClock.elapsedRealtimeNanos()}.png")
        assertTrue("expected UiDevice.takeScreenshot to succeed", device.takeScreenshot(file))
        return BitmapFactory.decodeFile(file.absolutePath)
            ?: error("expected screenshot bitmap to decode from ${file.absolutePath}")
    }

    private fun averageColor(bitmap: Bitmap, bounds: Rect, startFraction: Float, endFraction: Float): Rgb {
        val left = (bounds.left + bounds.width() * startFraction).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bounds.top + bounds.height() * startFraction).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = maxOf(
            left + 1,
            (bounds.left + bounds.width() * endFraction).roundToInt(),
        ).coerceAtMost(bitmap.width)
        val bottom = maxOf(
            top + 1,
            (bounds.top + bounds.height() * endFraction).roundToInt(),
        ).coerceAtMost(bitmap.height)

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }
        return Rgb(
            red = (red / count).toInt(),
            green = (green / count).toInt(),
            blue = (blue / count).toInt(),
        )
    }

    private fun assertApproxColor(name: String, actual: Rgb, expected: Rgb, tolerance: Int = 40) {
        assertTrue("$name red expected $expected got $actual", abs(actual.red - expected.red) <= tolerance)
        assertTrue("$name green expected $expected got $actual", abs(actual.green - expected.green) <= tolerance)
        assertTrue("$name blue expected $expected got $actual", abs(actual.blue - expected.blue) <= tolerance)
    }

    private suspend fun timeWarmIconCache(rows: List<AppInfo>, badgeEnabled: Boolean): Long {
        setShortcutBadgePref(badgeEnabled)
        clearIconCache()
        val style = IconStyle(
            shape = IconShape.SYSTEM,
            themed = false,
            background = Color.WHITE,
            foreground = Color.BLACK,
        )

        // warmIconCache does not take a shortcut-badge flag yet; until the feature lands this
        // times the same call on both sides while still locking in the performance budget.
        val start = SystemClock.elapsedRealtimeNanos()
        warmIconCache(
            context = targetContext.applicationContext,
            apps = rows,
            iconPack = null,
            overrides = emptyMap(),
            px = 192,
            style = style,
            priorityKeys = rows.map { it.key }.toSet(),
            badges = badgeEnabled,
        )
        return SystemClock.elapsedRealtimeNanos() - start
    }

    private fun shortcutRow(index: Int): AppInfo {
        val activity = if (index < 10) "Main" else "Main2"
        return AppInfo(
            componentName = ComponentName(TEST_PACKAGE, "$TEST_PACKAGE.$activity"),
            label = "Probe $index",
            user = Process.myUserHandle(),
            userSerial = 0L,
            kind = EntryKind.SHORTCUT,
            shortcutId = "probe_$index",
        )
    }

    private fun parentAppRows(): List<AppInfo> = listOf(
        AppInfo(
            componentName = ComponentName(TEST_PACKAGE, "$TEST_PACKAGE.Main"),
            label = "Probe host",
            user = Process.myUserHandle(),
            userSerial = 0L,
        ),
        AppInfo(
            componentName = ComponentName(TEST_PACKAGE, "$TEST_PACKAGE.Main2"),
            label = "Probe host 2",
            user = Process.myUserHandle(),
            userSerial = 0L,
        ),
    )

    private fun shortcutKey(id: String): String = EntryKeys.shortcut(TEST_PACKAGE, id, 0L)

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private data class Rgb(val red: Int, val green: Int, val blue: Int)

    companion object {
        private const val TAG = "ShortcutBadgeTest"
        private const val WAIT_MS = 10_000L
        private const val TEST_PACKAGE = "dev.victorialauncher.testpack"
        private const val SHORTCUT_APP_BADGE_PREF = "shortcut_app_badge"
        private const val PERF_RUNS = 7

        private val TEST_SHORTCUT_IDS = (0 until 20).map { "probe_$it" }

        private val CYAN = Rgb(0, 255, 255)
        private val MAGENTA = Rgb(255, 0, 255)
        private val YELLOW = Rgb(255, 255, 0)
        private val GREEN = Rgb(0, 200, 0)
    }
}
