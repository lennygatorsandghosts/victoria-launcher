// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSafeAreaMigrationTest {
    private val prefs get() = Prefs(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    private suspend fun seed(top: Int) {
        val values = JSONObject()
        mapOf("layout_defaults_version" to 2, "widget_pad_top" to top,
            "widget_pad_bottom" to 9, "now_playing_pad_top" to 61,
            "now_playing_pad_bottom" to 11, "favorites_pad_top" to 72,
            "favorites_pad_bottom" to 13).forEach { (key, value) ->
            values.put(key, JSONObject().put("type", "int").put("value", value))
        }
        assertTrue(prefs.importJson(JSONObject().put("format", 1).put("app", "Victoria Launcher").put("values", values).toString()))
    }

    private suspend fun verifyMigration(top: Int, expected: Int) {
        seed(top)
        val favorites = prefs.favorites.first()
        val folders = prefs.folders.first()
        prefs.migrateHomeSafeArea(32, PaddingSlot.WIDGET_TOP)
        assertEquals(HomePaddings(61, 11, expected, 9, 72, 13), prefs.homePaddings.first())
        assertEquals(3, prefs.layoutDefaultsVersion.first())
        val exported = JSONObject(prefs.exportJson(stripOtherProfiles = true).json).getJSONObject("values")
        assertEquals(32, exported.getJSONObject("layout_safe_top_at_migration").getInt("value"))
        assertEquals(favorites, prefs.favorites.first())
        assertEquals(folders, prefs.folders.first())
        // A later rotation or process recreation must not subtract again.
        prefs.migrateHomeSafeArea(48, PaddingSlot.WIDGET_TOP)
        assertEquals(expected, prefs.homePaddings.first().widgetTop)
        assertEquals(32, JSONObject(prefs.exportJson(stripOtherProfiles = true).json)
            .getJSONObject("values").getJSONObject("layout_safe_top_at_migration").getInt("value"))
    }

    @Test fun alreadyClearLayoutKeepsAllBlockCoordinates() = runBlocking {
        verifyMigration(90, 58)
        assertEquals(90, 32 + prefs.homePaddings.first().widgetTop)
    }

    @Test fun underBarLayoutMovesToSafeEdgeWithoutChangingLaterGaps() = runBlocking {
        verifyMigration(12, 0)
        assertEquals(32, 32 + prefs.homePaddings.first().widgetTop)
    }

    @Test fun favoritesFirstDoesNotSubtractFromWidgetOrNowPlayingGaps() = runBlocking {
        seed(90)
        prefs.migrateHomeSafeArea(32, PaddingSlot.FAVORITES_TOP)
        assertEquals(HomePaddings(61, 11, 90, 9, 40, 13), prefs.homePaddings.first())
    }

    @Test fun nowPlayingFirstMigratesOnlyItsLeadingGap() = runBlocking {
        seed(90)
        prefs.migrateHomeSafeArea(32, PaddingSlot.NOW_PLAYING_TOP)
        assertEquals(HomePaddings(29, 11, 90, 9, 72, 13), prefs.homePaddings.first())
    }

    @Test fun replacedImportRejectsStaleMigrationAndExposesNewSnapshot() = runBlocking {
        seed(90)
        val previous = prefs.homeLayoutMigrationState.first()
        seed(120)
        val replacement = prefs.homeLayoutMigrationState.first()
        assertEquals(previous.version, replacement.version)
        assertTrue("same-version imports still change the migration effect key", previous != replacement)
        prefs.migrateHomeSafeArea(32, PaddingSlot.WIDGET_TOP, previous)
        assertEquals(120, prefs.homePaddings.first().widgetTop)
        assertEquals(2, prefs.layoutDefaultsVersion.first())
        prefs.migrateHomeSafeArea(32, PaddingSlot.WIDGET_TOP, replacement)
        assertEquals(88, prefs.homePaddings.first().widgetTop)
        assertEquals(3, prefs.layoutDefaultsVersion.first())
    }
}
