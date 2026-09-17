// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.datastore.preferences.core.Preferences
import dev.victorialauncher.ui.common.ICON_PACK_OVERRIDE_PREFIX
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportValidationTest {

    private fun ParsedExport.value(name: String): Any? = values.firstOrNull { it.first.name == name }?.second

    private fun fixtureText(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("export-0.62.2.json")) {
            "export-0.62.2.json missing from test resources"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    // --- the stock fixture (see app/src/test/resources/export-0.62.2.json for how it was made) ---

    @Test
    fun `the stock 0-62-2 fixture parses and every one of its keys is accepted`() {
        val parsed = parseSettingsExport(fixtureText(), Prefs.importAllowList)
        assertNotNull(parsed)
        val rawKeyCount = JSONObject(fixtureText()).getJSONObject("values").length()
        assertEquals(16, rawKeyCount)
        assertEquals(rawKeyCount, parsed!!.values.size)
    }

    @Test
    fun `the stock fixture round-trips its scalar and set values`() {
        val parsed = parseSettingsExport(fixtureText(), Prefs.importAllowList)!!
        assertEquals(72, parsed.value("icon_size_dp"))
        assertEquals(20, parsed.value("label_size_sp"))
        assertTrue(parsed.value("themed_icons") as Boolean)
        assertFalse(parsed.value("haptics_enabled") as Boolean)
        assertEquals(0.6f, parsed.value("dim_wallpaper_alpha") as Float, 0.0001f)
        assertEquals("SERIF", parsed.value("font"))
        assertEquals(
            setOf("com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity"),
            parsed.value("hidden_apps"),
        )
    }

    @Test
    fun `the stock fixture's folders and legitimate icon overrides survive intact`() {
        val parsed = parseSettingsExport(fixtureText(), Prefs.importAllowList)!!
        val folders = foldersFromJson(parsed.value("folders_json") as String)
        assertEquals(2, folders.size)
        assertEquals("Media", folders.first { it.id == "f1" }.name)
        assertEquals("pack:com.example.iconpack:ic_tools", folders.first { it.id == "f2" }.icon)

        val icons = decodeStringMap(parsed.value("icon_overrides_json") as String)
        // One pack: override and one content:// override in the fixture, both of allowed
        // shape -- sanitizing legitimate values must be a no-op, not just a filter.
        assertEquals(2, icons.size)
        assertTrue(icons.values.any { it.startsWith(ICON_PACK_OVERRIDE_PREFIX) })
        assertTrue(icons.values.any { it.startsWith("content://") })
    }

    // --- envelope-level rejection: these return null, no entry is salvageable ---

    @Test
    fun `oversize input is refused`() {
        val huge = "x".repeat(MAX_IMPORT_FILE_BYTES + 1)
        assertNull(parseSettingsExport(huge, Prefs.importAllowList))
    }

    @Test
    fun `truncated JSON is refused`() {
        val truncated = """{"format":1,"app":"Victoria Launcher","values":{"font":{"type":"string","value":"SER"""
        assertNull(parseSettingsExport(truncated, Prefs.importAllowList))
    }

    @Test
    fun `a JSON value that isn't an object at the root is refused`() {
        assertNull(parseSettingsExport("""["not", "an", "object"]""", Prefs.importAllowList))
    }

    @Test
    fun `a file missing the values object is refused`() {
        assertNull(parseSettingsExport("""{"format":1,"app":"Victoria Launcher"}""", Prefs.importAllowList))
    }

    @Test
    fun `an empty string is refused`() {
        assertNull(parseSettingsExport("", Prefs.importAllowList))
    }

    @Test
    fun `a mismatched format major is refused`() {
        val text = """{"format":2,"app":"Victoria Launcher","values":{}}"""
        assertNull(parseSettingsExport(text, Prefs.importAllowList))
    }

    @Test(timeout = 5_000)
    fun `a mostly-nested file with no real envelope is refused quickly rather than overflowing slowly`() {
        // Well under the 1 MiB cap so this exercises org.json's recursive descent itself, not
        // the length guard in front of it -- org.json has no depth limit of its own, so this
        // overflows the parsing thread's stack in well under a second.
        val nested = "[".repeat(900_000)
        assertNull(parseSettingsExport(nested, Prefs.importAllowList))
    }

    // --- entry-level dropping: the envelope is fine, one bad row is skipped, the rest import ---

    @Test
    fun `an unknown key is ignored while a known key alongside it still imports`() {
        val text = envelopeOf(
            "font" to """{"type":"string","value":"MONOSPACE"}""",
            "some_future_setting_this_build_has_never_heard_of" to """{"type":"string","value":"whatever"}""",
        )
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertEquals(1, parsed.values.size)
        assertEquals("MONOSPACE", parsed.value("font"))
    }

    @Test
    fun `a type tag that disagrees with the key's declared type is dropped`() {
        // "font" is declared string; claiming it's an int here must not sneak an int into a
        // string preference.
        val text = envelopeOf("font" to """{"type":"int","value":1}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a value whose JSON type doesn't match its declared type is dropped even if the tag is right`() {
        // The tag says int, but the JSON value itself is a string -- org.json's own getInt
        // would happily coerce "5", which is exactly the leniency this guards against.
        val text = envelopeOf("icon_size_dp" to """{"type":"int","value":"5"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `Infinity from an out-of-range exponent is dropped rather than stored as a float`() {
        // 1e400 is a syntactically ordinary JSON number; it just doesn't fit in a Double,
        // and IEEE 754 rounds an out-of-range double to Infinity -- no literal "Infinity"
        // token needed to reach a non-finite float.
        val text = envelopeOf("dim_wallpaper_alpha" to """{"type":"float","value":1e400}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a quoted NaN string is dropped rather than coerced into a float`() {
        val text = envelopeOf("dim_wallpaper_alpha" to """{"type":"float","value":"NaN"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `an int value outside Int range is dropped rather than silently truncated`() {
        val text = envelopeOf("icon_size_dp" to """{"type":"int","value":99999999999}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a stringSet over the element cap is dropped`() {
        val hugeArray = JSONArray().apply { repeat(5_000) { put("com.example.app$it/Main") } }
        val text = envelopeOf("hidden_apps" to """{"type":"stringSet","value":$hugeArray}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a stringSet within the cap still imports`() {
        val text = envelopeOf("hidden_apps" to """{"type":"stringSet","value":["a/A","b/B"]}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertEquals(setOf("a/A", "b/B"), parsed.value("hidden_apps"))
    }

    @Test
    fun `malformed embedded JSON in folders_json is skipped rather than stored as garbage`() {
        val text = envelopeOf("folders_json" to """{"type":"string","value":"{not an array"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `malformed embedded JSON in name_overrides_json is skipped rather than stored as garbage`() {
        val text = envelopeOf("name_overrides_json" to """{"type":"string","value":"not json at all"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a well-formed empty JSON string value is kept, not treated as malformed`() {
        val text = envelopeOf("folders_json" to """{"type":"string","value":""}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertEquals("", parsed.value("folders_json"))
    }

    // --- icon override value shapes: AppIcon.decodeIconOverride only ever expects two of these ---

    @Test
    fun `a pack override is allowed`() {
        assertTrue(isAllowedIconOverrideValue("pack:com.example.iconpack:ic_home"))
    }

    @Test
    fun `a content URI override is allowed`() {
        assertTrue(isAllowedIconOverrideValue("content://media/external/images/media/1"))
    }

    @Test
    fun `a file URI override is rejected`() {
        assertFalse(isAllowedIconOverrideValue("file:///data/data/dev.victorialauncher/shared_prefs/victoria_prefs.xml"))
    }

    @Test
    fun `a javascript scheme override is rejected`() {
        assertFalse(isAllowedIconOverrideValue("javascript:alert(1)"))
    }

    @Test
    fun `a pack override with path traversal in either segment is rejected`() {
        assertFalse(isAllowedIconOverrideValue("pack:../../etc/passwd:ic_home"))
        assertFalse(isAllowedIconOverrideValue("pack:com.example.iconpack:../../ic_home"))
    }

    @Test
    fun `hostile icon override entries are dropped from icon_overrides_json while a legitimate one survives`() {
        val overrides = JSONObject()
            .put("com.a/com.a.Main", "pack:com.example.iconpack:ic_a")
            .put("com.b/com.b.Main", "file:///data/data/dev.victorialauncher/shared_prefs/victoria_prefs.xml")
            .put("com.c/com.c.Main", "javascript:alert(1)")
        val entry = JSONObject().put("type", "string").put("value", overrides.toString())
        val text = envelopeOf("icon_overrides_json" to entry.toString())
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        val kept = decodeStringMap(parsed.value("icon_overrides_json") as String)
        assertEquals(mapOf("com.a/com.a.Main" to "pack:com.example.iconpack:ic_a"), kept)
    }

    // --- the search template: the one value that decides where typed text is sent ---

    @Test
    fun `a usable search template imports`() {
        val text = envelopeOf("search_url_template" to """{"type":"string","value":"https://search.example.org/search?q=%s"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertEquals("https://search.example.org/search?q=%s", parsed.value("search_url_template"))
    }

    @Test
    fun `a search template that could never be used is dropped while its neighbours import`() {
        listOf(
            "javascript:alert(1)//%s",
            "intent://x#Intent;scheme=https;S.q=%s;end",
            "file:///sdcard/%s",
            "https://%s.evil.example/",
            "https://good.example@evil.example/?q=%s",
            "https://search.example.org/no-placeholder",
        ).forEach { hostile ->
            val text = envelopeOf(
                "search_url_template" to """{"type":"string","value":${JSONObject.quote(hostile)}}""",
                "search_label" to """{"type":"string","value":"Find"}""",
            )
            val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
            assertNull("expected the template to be dropped: $hostile", parsed.value("search_url_template"))
            assertEquals("Find", parsed.value("search_label"))
        }
    }

    @Test
    fun `an empty search template imports as the way to have no search entry`() {
        val text = envelopeOf("search_url_template" to """{"type":"string","value":""}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)!!
        assertEquals("", parsed.value("search_url_template"))
    }

    // --- charset ---

    @Test
    fun `a leading UTF-8 BOM is tolerated`() {
        val text = "\uFEFF" + envelopeOf("font" to """{"type":"string","value":"SANS_SERIF"}""")
        val parsed = parseSettingsExport(text, Prefs.importAllowList)
        assertNotNull(parsed)
        assertEquals("SANS_SERIF", parsed!!.value("font"))
    }

    // --- the allow-list itself ---

    @Test
    fun `every declared preference key is covered by the import allow-list`() {
        val declaredNames = Prefs.Keys.javaClass.declaredMethods
            .filter { it.parameterCount == 0 && Preferences.Key::class.java.isAssignableFrom(it.returnType) }
            .map { (it.invoke(Prefs.Keys) as Preferences.Key<*>).name }
            .toSet()
        assertTrue("Keys declares no preferences -- reflection found nothing to check", declaredNames.isNotEmpty())
        assertEquals(declaredNames, Prefs.importAllowList.keys)
    }

    private fun envelopeOf(vararg entries: Pair<String, String>): String {
        val body = entries.joinToString(",") { (name, json) -> "\"$name\":$json" }
        return """{"format":1,"app":"Victoria Launcher","values":{$body}}"""
    }

    /** [Prefs.jsonToMap]'s decode, reimplemented here since it's private to that class. */
    private fun decodeStringMap(json: String): Map<String, String> =
        JSONObject(json).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) }.toMap() }
}
