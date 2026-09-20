// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.victorialauncher.HostedWidgetFixture.Companion.BOTTOM_HANDLE
import dev.victorialauncher.HostedWidgetFixture.Companion.TOP_HANDLE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetResizeAccessibilityTest {
    private val fixture = HostedWidgetFixture()
    @Before fun setUp() = fixture.start()
    @After fun tearDown() = fixture.close()

    private fun openMore() = with(fixture) {
        enterResize()
        val more = device.wait(Until.findObject(By.desc("More")), 2_000L)
            ?: device.wait(Until.findObject(By.text("More")), 2_000L)
        assertTrue("In-place resize exposes More", more != null)
        checkNotNull(more).click()
    }

    private fun clickItem(resource: Int) = with(fixture) {
        val item = device.wait(Until.findObject(By.text(string(resource))), 5_000L)
        assertTrue("More retains menu item ${string(resource)}", item != null)
        assertTrue("Menu item is enabled: ${string(resource)}", checkNotNull(item).isEnabled)
        item.click()
    }

    @Test fun a5_moreRetainsAllSixItemsAndEditLayoutHeightControl() = with(fixture) {
        openMore()
        val items = listOf(R.string.action_app_info, R.string.action_edit_layout,
            R.string.widget_change_settings, R.string.widget_add_another,
            R.string.widget_remove_this, R.string.action_open_settings)
        items.forEach { id ->
            val node = device.wait(Until.findObject(By.text(string(id))), 5_000L)
            assertTrue("More retains reachable ${string(id)}", node != null && node.isEnabled)
        }
        clickItem(R.string.action_edit_layout)
        assertTrue("Edit layout still opens the complete layout controls",
            device.wait(Until.hasObject(By.text(string(R.string.handle_side_padding))), 5_000L))
        repeat(3) {
            if (!device.hasObject(By.text(string(R.string.handle_widget_height)))) {
                device.swipe(device.displayWidth / 2, (device.displayHeight * .82f).toInt(),
                    device.displayWidth / 2, (device.displayHeight * .30f).toInt(), 45)
            }
        }
        assertTrue("Original height stepper remains accessible through Edit layout",
            device.hasObject(By.text(string(R.string.handle_widget_height))))
        assertTrue("Decrease stepper action remains accessible",
            device.hasObject(By.desc(string(R.string.settings_less))))
        assertTrue("Increase stepper action remains accessible",
            device.hasObject(By.desc(string(R.string.settings_more))))
        val value = device.findObject(By.text("${heightPref()}dp"))
        assertTrue("Height numeric entry remains reachable", value != null)
        checkNotNull(value).click()
        assertTrue("Height numeric entry opens an editable field",
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000L))
    }

    @Test fun a5_moreOpensWidgetConfiguration() = with(fixture) {
        openMore()
        clickItem(R.string.widget_change_settings)
        assertTrue("Widget configuration remains reachable",
            device.wait(Until.hasObject(By.text(string(R.string.widget_clock_label))), 5_000L))
        assertTrue("Widget configuration exposes clock controls",
            device.hasObject(By.text(string(R.string.widget_clock_hour))))
    }

    @Test fun a5_moreOpensWidgetPicker() = with(fixture) {
        openMore()
        clickItem(R.string.widget_add_another)
        assertTrue("Add another opens the widget picker",
            device.wait(Until.hasObject(By.text(string(R.string.widget_add))), 5_000L))
    }

    @Test fun a5_moreOpensLauncherSettings() = with(fixture) {
        openMore()
        clickItem(R.string.action_open_settings)
        assertTrue("Launcher settings remain reachable",
            device.wait(Until.hasObject(By.text(string(R.string.settings_title))), 5_000L))
    }

    @Test fun a5_moreOpensSystemAppInfo() = with(fixture) {
        openMore()
        clickItem(R.string.action_app_info)
        await("System app-info activity takes focus") {
            device.currentPackageName?.let { it != app.packageName && it.endsWith("settings") } == true
        }
        assertTrue("System details identify the widget provider",
            device.wait(Until.hasObject(By.text(string(R.string.app_name))), 5_000L))
    }

    @Test fun a5_moreRemovesHostedWidget() = with(fixture) {
        openMore()
        clickItem(R.string.widget_remove_this)
        await("Remove this widget removes the hosted widget id") {
            runBlocking { widgetId !in app.prefs.widgetIds.first() }
        }
    }

    @Test fun a6_bothHandlesExposeAndExecuteOneDpCustomActions() = with(fixture) {
        enterResize()
        for (description in listOf(BOTTOM_HANDLE, TOP_HANDLE)) {
            val initialHeight = heightPref()
            val initialTop = topPref()
            val before = bounds()
            performCustomAction(description, "Increase height by 1 dp")
            await("$description increase persists exactly 1dp") {
                heightPref() == initialHeight + 1 &&
                    topPref() == initialTop - (if (description == TOP_HANDLE) 1 else 0)
            }
            assertEquals("Increase is exactly one dp", initialHeight + 1, heightPref())
            if (description == TOP_HANDLE) {
                assertDp("Top increase preserves rendered bottom", before.bottom / density, bounds().bottom / density)
            } else {
                assertDp("Bottom increase preserves rendered top", before.top / density, bounds().top / density)
            }
            performCustomAction(description, "Decrease height by 1 dp")
            await("$description decrease restores both preferences") {
                heightPref() == initialHeight && topPref() == initialTop
            }
            assertDp("Accessibility increase/decrease restores host height", before.height() / density, bounds().height() / density)
        }
    }

    private fun performCustomAction(description: String, label: String) = with(fixture) {
        var node: AccessibilityNodeInfo? = null
        await("Accessible handle $description") {
            node = findNode(instrumentation.uiAutomation.rootInActiveWindow, description)
            node != null
        }
        val action = checkNotNull(node).actionList.firstOrNull { it.label?.toString() == label }
        assertTrue("$description exposes custom action '$label'", action != null)
        assertTrue("$description executes custom action '$label'", node!!.performAction(checkNotNull(action).id))
    }

    private fun findNode(node: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == description) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), description)?.let { return it }
        return null
    }
}
