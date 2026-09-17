// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUrlTest {

    private fun assertOk(template: String, insecure: Boolean = false) {
        val result = SearchUrl.validate(template)
        assertTrue("expected Ok for \"$template\" but got $result", result is SearchUrl.Validation.Ok)
        assertEquals(insecure, (result as SearchUrl.Validation.Ok).insecure)
    }

    private fun assertInvalid(template: String, reason: SearchUrl.Reason? = null) {
        val result = SearchUrl.validate(template)
        assertTrue("expected Invalid for \"$template\" but got $result", result is SearchUrl.Validation.Invalid)
        if (reason != null) {
            assertEquals(reason, (result as SearchUrl.Validation.Invalid).reason)
        }
    }

    // --- valid templates ---

    @Test
    fun `an https template with the placeholder in the query is valid`() {
        assertOk("https://search.example.org/search?q=%s")
    }

    @Test
    fun `an http template is valid but marked insecure`() {
        assertOk("http://search.example.org/search?q=%s", insecure = true)
    }

    @Test
    fun `the placeholder may sit in the path`() {
        assertOk("https://search.example.org/%s")
    }

    @Test
    fun `the placeholder may sit in the fragment`() {
        assertOk("https://search.example.org/search#%s")
    }

    @Test
    fun `surrounding whitespace is trimmed before anything else is checked`() {
        assertOk("  https://search.example.org/search?q=%s  \n")
    }

    @Test
    fun `an uppercase scheme is accepted`() {
        assertOk("HTTPS://search.example.org/search?q=%s")
    }

    @Test
    fun `a unicode host passes through unchanged`() {
        assertOk("https://пример.рф/search?q=%s")
    }

    // --- each rejection rule ---

    @Test
    fun `a blank template is rejected`() {
        assertInvalid("", SearchUrl.Reason.BLANK)
        assertInvalid("   \n\t  ", SearchUrl.Reason.BLANK)
    }

    @Test
    fun `a template over 2048 characters is rejected`() {
        val padding = "a".repeat(2048)
        assertInvalid("https://search.example.org/$padding?q=%s", SearchUrl.Reason.TOO_LONG)
    }

    @Test
    fun `a template exactly at the length limit is accepted`() {
        val prefix = "https://search.example.org/?q=%s&pad="
        val template = prefix + "a".repeat(MAX_LENGTH - prefix.length)
        assertEquals(MAX_LENGTH, template.length)
        assertOk(template)
    }

    @Test
    fun `an embedded newline is rejected even though the ends are trimmed`() {
        assertInvalid("https://search.example.org/search?q=%s\nEVIL", SearchUrl.Reason.CONTROL_CHARACTER)
    }

    @Test
    fun `an embedded carriage return is rejected`() {
        assertInvalid("https://search.example.org/\r/search?q=%s", SearchUrl.Reason.CONTROL_CHARACTER)
    }

    @Test
    fun `an embedded tab is rejected`() {
        assertInvalid("https://search.example.org/\t/search?q=%s", SearchUrl.Reason.CONTROL_CHARACTER)
    }

    @Test
    fun `no placeholder at all is rejected`() {
        assertInvalid("https://search.example.org/search?q=test", SearchUrl.Reason.NO_PLACEHOLDER)
    }

    @Test
    fun `two placeholders are rejected`() {
        assertInvalid("https://search.example.org/?a=%s&b=%s", SearchUrl.Reason.MULTIPLE_PLACEHOLDERS)
    }

    @Test
    fun `an unsupported scheme is rejected`() {
        assertInvalid("ftp://search.example.org/%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `javascript is rejected`() {
        assertInvalid("javascript:alert(1)//%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `intent is rejected`() {
        assertInvalid("intent://x#Intent;package=com.evil;S.q=%s;end", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `file is rejected`() {
        assertInvalid("file:///etc/passwd?q=%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `content is rejected`() {
        assertInvalid("content://com.evil.provider/%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `data is rejected`() {
        assertInvalid("data:text/plain,%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `about is rejected`() {
        assertInvalid("about:blank%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `market is rejected`() {
        assertInvalid("market://details?id=%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `tel is rejected`() {
        assertInvalid("tel:%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `scheme-relative input is rejected`() {
        assertInvalid("//search.example.org/search?q=%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `scheme-less input is rejected`() {
        assertInvalid("search.example.org/search?q=%s", SearchUrl.Reason.UNSUPPORTED_SCHEME)
    }

    @Test
    fun `userinfo is rejected outright`() {
        assertInvalid("https://good.example@evil.example/?q=%s", SearchUrl.Reason.USERINFO_NOT_ALLOWED)
    }

    @Test
    fun `a placeholder inside the host is rejected`() {
        assertInvalid("https://%s.evil.example/", SearchUrl.Reason.PLACEHOLDER_IN_AUTHORITY)
    }

    @Test
    fun `a placeholder as the port is rejected`() {
        assertInvalid("https://good.example:%s/", SearchUrl.Reason.PLACEHOLDER_IN_AUTHORITY)
    }

    @Test
    fun `a placeholder as the whole scheme is rejected`() {
        assertInvalid("%s://good.example/")
    }

    @Test
    fun `an empty host is rejected`() {
        assertInvalid("https:///search?q=%s", SearchUrl.Reason.MISSING_HOST)
    }

    // --- build(): encoding ---

    @Test
    fun `spaces and special characters are percent-encoded, not plus-encoded`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "a b&c=d#e")
        assertEquals("https://search.example.org/search?q=a%20b%26c%3Dd%23e", built)
    }

    @Test
    fun `a literal percent sign is escaped`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "100%")
        assertEquals("https://search.example.org/search?q=100%25", built)
    }

    @Test
    fun `a literal plus sign is escaped, never taken to mean space`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "1+1")
        assertEquals("https://search.example.org/search?q=1%2B1", built)
    }

    @Test
    fun `quote characters are escaped`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "\"quoted\" 'and' this")
        assertEquals("https://search.example.org/search?q=%22quoted%22%20%27and%27%20this", built)
    }

    @Test
    fun `an emoji is escaped as its utf-8 bytes`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "\uD83D\uDE00")
        assertEquals("https://search.example.org/search?q=%F0%9F%98%80", built)
    }

    @Test
    fun `an empty query builds nothing`() {
        assertNull(SearchUrl.build("https://search.example.org/search?q=%s", ""))
    }

    @Test
    fun `a blank query builds nothing`() {
        assertNull(SearchUrl.build("https://search.example.org/search?q=%s", "   "))
    }

    @Test
    fun `an invalid template builds nothing regardless of the query`() {
        assertNull(SearchUrl.build("ftp://search.example.org/%s", "anything"))
    }

    @Test
    fun `an injected query can never add a parameter, a fragment or a newline`() {
        val built = SearchUrl.build("https://search.example.org/search?q=%s", "x&evil=1#frag\nY: 1")
        checkNotNull(built)
        assertTrue(built.startsWith("https://search.example.org/search?q="))
        val encodedQuery = built.removePrefix("https://search.example.org/search?q=")
        // Nothing from the injected text survived as a URI-structural character.
        assertTrue(setOf('&', '=', '#', '\n', ' ').none { it in encodedQuery })
    }

    @Test
    fun `the built url re-parses to the same host the template named`() {
        val template = "https://search.example.org/search?q=%s"
        val built = SearchUrl.build(template, "hello world")
        checkNotNull(built)
        val templateHost = URI(template.replaceFirst("%s", "x")).host
        val builtHost = URI(built).host
        assertEquals(templateHost, builtHost)
    }

    @Test
    fun `the built url re-parses to the same host for a unicode template`() {
        val template = "https://пример.рф/search?q=%s"
        val built = SearchUrl.build(template, "hello")
        checkNotNull(built)
        val templateAuthority = URI(template.replaceFirst("%s", "x")).rawAuthority
        val builtAuthority = URI(built).rawAuthority
        assertEquals(templateAuthority, builtAuthority)
    }

    companion object {
        /** Mirrors SearchUrl's private MAX_LENGTH so the boundary test doesn't drift from it. */
        private const val MAX_LENGTH = 2048
    }
}
