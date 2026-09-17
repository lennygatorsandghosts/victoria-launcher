// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonActionTest {

    @Test
    fun `every action round-trips through its stored string`() {
        val actions = listOf(
            ButtonAction.None,
            ButtonAction.LaunchEntry("com.example/com.example.Main"),
            ButtonAction.WebSearch,
            ButtonAction.SearchApps,
            ButtonAction.Notifications,
            ButtonAction.LockScreen,
            ButtonAction.LauncherSettings,
            ButtonAction.TogglePrivateSpace,
            ButtonAction.OpenUrl("https://example.org/path?q=1"),
            ButtonAction.OpenUrl("http://example.org/path?q=1"),
        )

        actions.forEach { action ->
            assertEquals(action, ButtonAction.parse(action.encode()))
        }
    }

    @Test
    fun `unknown action strings parse as null`() {
        listOf(
            null,
            "",
            " ",
            "search",
            "notification",
            "entry:",
            "url:",
            "web:",
            "apps:",
        ).forEach { raw ->
            assertNull("expected null for $raw", ButtonAction.parse(raw))
        }
    }

    @Test
    fun `hostile non-url strings parse as null`() {
        listOf(
            "entry:   ",
            "none:entry:com.example/Main",
            "url:javascript:alert(1)",
            "url:intent://x#Intent;scheme=https;end",
            "url:file:///data/data/dev.victorialauncher/prefs",
        ).forEach { raw ->
            assertNull("expected null for $raw", ButtonAction.parse(raw))
        }
    }

    @Test
    fun `absolute http and https URLs with hosts are accepted`() {
        assertEquals(ButtonAction.OpenUrl("https://example.org"), ButtonAction.parse("url:https://example.org"))
        assertEquals(ButtonAction.OpenUrl("http://example.org/path?q=1#top"), ButtonAction.parse("url:http://example.org/path?q=1#top"))
        assertEquals(ButtonAction.OpenUrl("https://[2001:db8::1]/search"), ButtonAction.parse("url:https://[2001:db8::1]/search"))
        assertEquals(ButtonAction.OpenUrl("https://пример.рф/search"), ButtonAction.parse("url:https://пример.рф/search"))
    }

    @Test
    fun `OpenUrl rejects unsupported schemes`() {
        listOf(
            "ftp://example.org",
            "javascript:alert(1)",
            "intent://x#Intent;scheme=https;end",
            "file:///etc/passwd",
            "content://com.example.provider/item",
            "data:text/plain,hello",
            "market://details?id=dev.victorialauncher",
            "tel:5551212",
        ).forEach { url ->
            assertNull("expected $url to be rejected", ButtonAction.parse("url:$url"))
        }
    }

    @Test
    fun `OpenUrl rejects relative scheme-less and missing-host URLs`() {
        listOf(
            "//example.org/path",
            "example.org/path",
            "/path",
            "https:///path",
            "https://",
            "https://:443/path",
        ).forEach { url ->
            assertNull("expected $url to be rejected", ButtonAction.parse("url:$url"))
        }
    }

    @Test
    fun `OpenUrl rejects userinfo in the authority`() {
        assertNull(ButtonAction.parse("url:https://good.example@evil.example/path"))
        assertNull(ButtonAction.parse("url:https://user:pass@example.org/path"))
    }

    @Test
    fun `OpenUrl rejects percent escapes in the authority`() {
        listOf(
            "https://good.example%40evil.example/path",
            "https://good.example%2Fevil.example/path",
            "https://good.example%5Cevil.example/path",
            "https://[fe80::1%25eth0]/path",
        ).forEach { url ->
            assertNull("expected $url to be rejected", ButtonAction.parse("url:$url"))
        }
    }

    @Test
    fun `OpenUrl rejects backslashes anywhere`() {
        assertNull(ButtonAction.parse("url:https:\\\\evil.example/path"))
        assertNull(ButtonAction.parse("url:https://good.example\\@evil.example/path"))
        assertNull(ButtonAction.parse("url:https://good.example/\\evil"))
    }

    @Test
    fun `OpenUrl rejects control characters`() {
        assertNull(ButtonAction.parse("url:https://example.org/path\nHeader: value"))
        assertNull(ButtonAction.parse("url:https://example.org/path\rHeader: value"))
        assertNull(ButtonAction.parse("url:https://example.org/path\tTabbed"))
    }

    @Test
    fun `OpenUrl rejects URLs over 512 characters`() {
        val prefix = "https://example.org/?q="
        val exactly512 = prefix + "a".repeat(512 - prefix.length)
        val tooLong = prefix + "a".repeat(513 - prefix.length)
        assertEquals(ButtonAction.OpenUrl(exactly512), ButtonAction.parse("url:$exactly512"))
        assertNull(ButtonAction.parse("url:$tooLong"))
    }

    @Test
    fun `effectiveActions uses defaults when slots are absent`() {
        assertEquals(
            mapOf(
                ButtonSlot.TAP to ButtonAction.WebSearch,
                ButtonSlot.SWIPE_UP to ButtonAction.SearchApps,
                ButtonSlot.SWIPE_LEFT to ButtonAction.None,
                ButtonSlot.SWIPE_RIGHT to ButtonAction.None,
            ),
            effectiveActions(emptyMap(), hasSearchTemplate = true, quickLeftKey = null, quickRightKey = null),
        )

        assertEquals(ButtonAction.SearchApps, effectiveActions(emptyMap(), false, null, null)[ButtonSlot.TAP])
    }

    @Test
    fun `effectiveActions migrates quick-launch keys for left and right defaults`() {
        val actions = effectiveActions(emptyMap(), hasSearchTemplate = true, quickLeftKey = "com.left/Main", quickRightKey = "com.right/Main")
        assertEquals(ButtonAction.LaunchEntry("com.left/Main"), actions[ButtonSlot.SWIPE_LEFT])
        assertEquals(ButtonAction.LaunchEntry("com.right/Main"), actions[ButtonSlot.SWIPE_RIGHT])
    }

    @Test
    fun `effectiveActions lets stored values win over defaults and migrated quick-launch keys`() {
        val stored = mapOf(ButtonSlot.TAP to "lock", ButtonSlot.SWIPE_LEFT to "none", ButtonSlot.SWIPE_RIGHT to "url:https://example.org")
        val actions = effectiveActions(stored, hasSearchTemplate = true, quickLeftKey = "com.left/Main", quickRightKey = "com.right/Main")
        assertEquals(ButtonAction.LockScreen, actions[ButtonSlot.TAP])
        assertEquals(ButtonAction.None, actions[ButtonSlot.SWIPE_LEFT])
        assertEquals(ButtonAction.OpenUrl("https://example.org"), actions[ButtonSlot.SWIPE_RIGHT])
    }
}
