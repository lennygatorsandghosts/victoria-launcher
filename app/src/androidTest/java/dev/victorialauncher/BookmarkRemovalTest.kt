// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.data.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkRemovalTest {

    private lateinit var device: UiDevice
    private lateinit var targetPackage: String

    private val pinnedIds = mutableListOf<String>()

    @Before
    fun setUp() {
        device = LauncherTestUtils.uiDevice()
        targetPackage = LauncherTestUtils.targetPackageName()
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    @After
    fun tearDown() = runBlocking {
        device.setOrientationNatural()
        device.unfreezeRotation()

        if (pinnedIds.isEmpty()) return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        runCatching { launcherApps.pinShortcuts(targetPackage, emptyList(), Process.myUserHandle()) }
        val prefs = Prefs(context.applicationContext)
        pinnedIds.forEach { id -> prefs.forgetEntry(EntryKeys.shortcut(targetPackage, id, 0L)) }
        pinnedIds.clear()
    }

    @Test
    fun homeOnlyRemovalKeepsTheBookmarkInTheListAndPinned() {
        val id = "bookmark-removal-home-only"
        val label = "Bookmark home-only removal"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        pinThroughTheConfirmation(id, label)

        longPressHomeShortcut(label)
        tapText(context.getString(R.string.action_remove), "home-only removal")

        assertTrue(
            "expected the bookmark to leave the home screen",
            device.wait(Until.gone(By.text(label)), WAIT_MS),
        )
        assertInAppList(label, "home-only")
        assertShortcutPinned(id)
    }

    @Test
    fun menuSaysWhatEachRemovalDoes() {
        val id = "bookmark-removal-menu-labels"
        val label = "Bookmark menu labels"
        pinThroughTheConfirmation(id, label)

        longPressHomeShortcut(label)

        assertTrue(
            "expected the home menu to say exactly what home-only removal does",
            device.wait(Until.hasObject(By.text("Remove from home screen")), WAIT_MS),
        )
        assertTrue(
            "expected the home menu to offer bookmark deletion",
            device.wait(Until.hasObject(By.text("Delete bookmark")), WAIT_MS),
        )
        assertFalse(
            "the home menu must not use ambiguous plain Remove",
            device.hasObject(By.text("Remove")),
        )
        assertFalse(
            "the home menu must not say Remove shortcut",
            device.hasObject(By.text("Remove shortcut")),
        )
    }

    @Test
    fun deleteBookmarkAsksFirstAndCancelChangesNothing() {
        val id = "bookmark-removal-cancel"
        val label = "Bookmark cancel removal"
        pinThroughTheConfirmation(id, label)

        longPressHomeShortcut(label)
        tapText("Delete bookmark", "bookmark deletion")
        assertTrue(
            "expected bookmark deletion to ask for confirmation first",
            device.wait(Until.hasObject(By.text("Delete this bookmark?")), WAIT_MS),
        )
        tapText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(android.R.string.cancel),
            "Cancel",
        )

        assertTrue(
            "expected Cancel to leave the bookmark on the home screen",
            LauncherTestUtils.waitForText(label),
        )
        assertInAppList(label, "cancel")
        assertShortcutPinned(id)
    }

    @Test
    fun deleteBookmarkConfirmedRemovesItEverywhere() {
        val id = "bookmark-removal-confirmed"
        val label = "Bookmark confirmed removal"
        pinThroughTheConfirmation(id, label)

        longPressHomeShortcut(label)
        tapText("Delete bookmark", "bookmark deletion")
        assertTrue(
            "expected bookmark deletion to ask for confirmation first",
            device.wait(Until.hasObject(By.text("Delete this bookmark?")), WAIT_MS),
        )
        tapText("Delete", "Delete")

        assertTrue(
            "expected the bookmark to leave the home screen",
            device.wait(Until.gone(By.text(label)), WAIT_MS),
        )
        assertNotInAppList(label, "confirmed")
        assertShortcutNotPinned(id)
    }

    @Test
    fun listMenuDeleteBookmarkAsksFirst() {
        val id = "bookmark-removal-list-menu"
        val label = "Bookmark list menu removal"
        pinThroughTheConfirmation(id, label)

        LauncherTestUtils.openAppList()
        LauncherTestUtils.filterAppList("list menu")
        val row = device.wait(Until.findObject(By.text(label)), WAIT_MS)
            ?: error("expected the bookmark row in the app list")
        row.longClick()

        tapText("Delete bookmark", "bookmark deletion")
        assertTrue(
            "expected bookmark deletion to ask for confirmation first",
            device.wait(Until.hasObject(By.text("Delete this bookmark?")), WAIT_MS),
        )
        tapText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(android.R.string.cancel),
            "Cancel",
        )

        assertShortcutPinned(id)
    }

    private fun pinThroughTheConfirmation(id: String, label: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        assertTrue(
            "the launcher under test must be the default home before it can host a pin request",
            manager.isRequestPinShortcutSupported,
        )

        val shortcut = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")))
            .build()
        assertTrue(
            "the pin request was refused before any confirmation was shown",
            manager.requestPinShortcut(shortcut, null),
        )
        pinnedIds += id

        val add = device.wait(Until.findObject(By.text("Add")), WAIT_MS)
            ?: error("expected the pin confirmation to offer Add")
        add.click()

        LauncherTestUtils.goHome()
        assertTrue(
            "expected the accepted shortcut to be listed on the home screen",
            LauncherTestUtils.waitForText(label),
        )
    }

    private fun longPressHomeShortcut(label: String) {
        val row = device.wait(Until.findObject(By.text(label)), WAIT_MS)
            ?: error("expected the bookmark row on the home screen")
        row.longClick()
    }

    private fun tapText(text: String, description: String) {
        val item = device.wait(Until.findObject(By.text(text)), WAIT_MS)
            ?: error("expected to find $description")
        item.click()
    }

    private fun assertInAppList(label: String, query: String) {
        LauncherTestUtils.openAppList()
        LauncherTestUtils.filterAppList(query)
        assertTrue(
            "expected the bookmark to stay in the A-Z list",
            LauncherTestUtils.waitForText(label),
        )
    }

    private fun assertNotInAppList(label: String, query: String) {
        LauncherTestUtils.openAppList()
        LauncherTestUtils.filterAppList(query)
        assertFalse(
            "expected the bookmark to leave the A-Z list",
            device.wait(Until.hasObject(By.text(label)), WAIT_MS),
        )
    }

    private fun assertShortcutPinned(id: String) {
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ShortcutManager::class.java)
        assertTrue(
            "expected the bookmark to remain pinned with the system",
            manager.pinnedShortcuts.any { it.id == id },
        )
    }

    private fun assertShortcutNotPinned(id: String) {
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ShortcutManager::class.java)
        assertFalse(
            "expected the bookmark to be unpinned with the system",
            manager.pinnedShortcuts.any { it.id == id },
        )
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
