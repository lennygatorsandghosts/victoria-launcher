// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.victorialauncher.ui.common.ICON_PACK_OVERRIDE_PREFIX
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A settings file comes from the SAF document picker, so it could be anything -- another app's
 * export, something hand-edited, or something deliberately hostile. Applied twice: once as a
 * byte count while the SAF stream is still being read (`VictoriaNavHost`), before a single byte
 * becomes a String, and again here as a backstop for any other caller of [parseSettingsExport].
 * `readText()` on an unbounded stream had no limit at all.
 */
internal const val MAX_IMPORT_FILE_BYTES = 1 * 1024 * 1024

/** A single crafted `stringSet` array or string value shouldn't be able to blow up memory either. */
private const val MAX_STRING_SET_ELEMENTS = 4096
private const val MAX_STRING_SET_ELEMENT_LENGTH = 4096
private const val MAX_STRING_VALUE_LENGTH = MAX_IMPORT_FILE_BYTES

/** The `type` tags [Prefs.exportJson] writes; [Prefs.importJson] must see the same one back. */
internal enum class ExpectedType(val tag: String) {
    BOOLEAN("boolean"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    STRING("string"),
    STRING_SET("stringSet"),
    ;

    companion object {
        fun fromTag(tag: String): ExpectedType? = entries.firstOrNull { it.tag == tag }
    }
}

/** The values [Prefs.importJson] is about to write, already type-checked and capped. */
internal class ParsedExport(val values: List<Pair<Preferences.Key<*>, Any>>)

/**
 * The type [Prefs.importAllowList] restores a name under, and -- for INT/FLOAT -- the semantic
 * range a legitimate value has to sit inside, beyond just the type's own machine range. The
 * Int/Float *machine* range is already enforced by [readTypedValue]; [range] catches a value
 * that fits the type but makes no sense as this specific setting -- a negative side padding,
 * say, which later throws out of `Modifier.padding`, or an icon size in the thousands, which
 * likely overflows Compose's layout constraints. Every INT/FLOAT entry in
 * [Prefs.importAllowList] declares one, even where that only means "the type's own full range"
 * (an ARGB color, say, where the sign bit is just the alpha byte) -- a reflection-based test in
 * ImportValidationTest fails if a numeric key is added without one, the same way the
 * allow-list-coverage test already does for a missing key.
 */
internal data class KnownPreference(val type: ExpectedType, val range: NumericRange? = null)

/** A closed range for one INT or FLOAT preference; see [KnownPreference.range]. */
internal sealed class NumericRange {
    data class OfInt(val min: Int, val max: Int) : NumericRange()
    data class OfFloat(val min: Float, val max: Float) : NumericRange()
}

/**
 * The four string-valued preferences that are themselves a JSON document, and so need a second,
 * shape-level check beyond "is a string under the length cap".
 */
private val JSON_STRING_KEYS = setOf(
    "folders_json",
    "name_overrides_json",
    "icon_overrides_json",
    "launch_counts_json",
)

/**
 * A path is never carried inside the export itself, only named by it -- see [sanitizeFontFile].
 */
private const val FONT_FILE_KEY_NAME = "font_file"

/**
 * Parses and fully validates a settings export before anything is written.
 *
 * Pure: no Android or DataStore-runtime types beyond [Preferences.Key] and [File] itself --
 * [File] is never touched as a filesystem object here, only compared as a path, which behaves
 * identically under a plain JVM unit test and on-device. [known] is the allow-list of real
 * preference names, their declared type, and -- for INT/FLOAT -- the range a legitimate value
 * has to sit inside (see [KnownPreference]); [Prefs.importAllowList] is the one built from
 * [Prefs.Keys] and used in production, kept in sync with it by a reflection-based test rather
 * than by two hand-written lists agreeing by luck. [allowedFontFileDir] is the one directory a
 * `font_file` value is allowed to resolve inside (see [sanitizeFontFile]); passed in rather than
 * read from a `Context` so this stays callable from a plain JVM test.
 *
 * A name that isn't in [known] is dropped, not rejected -- so a file exported by a newer build,
 * with a setting this one doesn't know about yet, still restores everything it does recognize
 * instead of refusing the whole file. A name it does know, but whose `type` tag doesn't match
 * the declared type, or whose value doesn't check out (wrong JSON type, non-finite number,
 * out-of-range number, over a size cap, outside its declared [NumericRange], a `font_file`
 * outside [allowedFontFileDir], or -- for the four JSON-shaped string keys -- not actually valid
 * JSON of the expected shape) is dropped the same way, one entry at a time, rather than failing
 * the whole import over a single bad row.
 *
 * Returns null when the file isn't recognizable as a Victoria Launcher export at all: too
 * large, not JSON, missing the envelope's `values` object, a `format` major this build doesn't
 * understand, or the `values` object comes out empty -- whether it started that way, or it had
 * entries and none of them survived validation. [Prefs.exportJson] never writes an empty
 * `values` object itself: [Prefs.ensureInstallMarker] guarantees at least one preference (the
 * install marker) is set before an export is ever possible, so an empty one reaching here is
 * never a genuine backup -- it's a hand-edited or otherwise-produced file, and importing it
 * would silently wipe every existing setting for nothing recognizable in return. The caller
 * still confirms with the user before writing anything (see [Prefs.parseImport] and
 * [Prefs.applyImport] in `Prefs.kt`), which is the other half of not letting an import surprise
 * anyone.
 */
internal fun parseSettingsExport(
    text: String,
    known: Map<String, KnownPreference>,
    allowedFontFileDir: File? = null,
): ParsedExport? {
    if (text.length > MAX_IMPORT_FILE_BYTES) return null
    // Some tools still emit one; it is invisible in most editors, and rejecting an otherwise
    // valid file over it would be a strange way to fail.
    val cleaned = text.removePrefix("\uFEFF")

    // runCatching also catches a StackOverflowError, which is the realistic failure mode for
    // adversarial nesting depth (e.g. a file that is nothing but "[[[[[...") -- org.json's
    // descent is recursive with no depth limit of its own, and the default JVM/ART thread
    // stack is nowhere near deep enough for the bracket count that fits in a 1 MiB file, so
    // parsing that shape fails fast with an overflow rather than succeeding slowly.
    return runCatching {
        val root = JSONObject(cleaned)
        if (root.optInt("format") != EXPORT_FORMAT) return@runCatching null
        val values = root.optJSONObject("values") ?: return@runCatching null

        val result = mutableListOf<Pair<Preferences.Key<*>, Any>>()
        values.keys().forEach { name ->
            val expected = known[name] ?: return@forEach
            val entry = values.optJSONObject(name) ?: return@forEach
            if (ExpectedType.fromTag(entry.optString("type", "")) != expected.type) return@forEach
            var value = readTypedValue(entry, expected.type) ?: return@forEach
            if (!isInDeclaredRange(value, expected.range)) return@forEach
            if (name in JSON_STRING_KEYS) {
                value = sanitizeJsonStringValue(name, value as String) ?: return@forEach
            }
            if (name == FONT_FILE_KEY_NAME) {
                value = sanitizeFontFile(value as String, allowedFontFileDir) ?: return@forEach
            }
            result.add(preferenceKey(name, expected.type) to value)
        }
        // See the doc comment above -- an empty result, whether the file started that way or
        // just ended up that way once nothing in it checked out, is refused rather than
        // imported as "restore me to nothing."
        if (result.isEmpty()) return@runCatching null
        ParsedExport(result)
    }.getOrNull()
}

/** [KnownPreference.range], applied. `null` means the type's machine range is the whole story. */
private fun isInDeclaredRange(value: Any, range: NumericRange?): Boolean = when (range) {
    null -> true
    is NumericRange.OfInt -> value is Int && value in range.min..range.max
    is NumericRange.OfFloat -> value is Float && value in range.min..range.max
}

/**
 * The font picker only ever writes a path under this app's own files directory --
 * `VictoriaNavHost`'s `onPickFontFile` copies the picked document into `context.filesDir` before
 * ever calling [Prefs.setFontFile] with the copy's path -- and an export never carries the font
 * file itself, so a `font_file` naming anywhere else names nothing this device can load, and it
 * later reaches `Typeface.createFromFile` with no check of its own otherwise. [allowedDir] is
 * `null` for a caller that has no directory to check against (a JVM test that isn't exercising
 * this path); every `font_file` value is dropped in that case, since there is nothing to verify
 * it against.
 *
 * Both sides are canonicalized before the containment check, not compared as raw strings, and
 * compared as a [java.nio.file.Path] rather than a string prefix -- an un-resolved path would
 * not catch a `<filesDir>/../elsewhere` that walks back out of the allowed directory with a
 * literal `..` segment, and a plain `String.startsWith` would wrongly accept a sibling directory
 * whose name happens to extend the allowed one (`.../files2` "starts with" `.../files`).
 */
private fun sanitizeFontFile(path: String, allowedDir: File?): String? {
    if (allowedDir == null) return null
    val candidate = runCatching { File(path).canonicalFile.toPath() }.getOrNull() ?: return null
    val root = runCatching { allowedDir.canonicalFile.toPath() }.getOrNull() ?: return null
    return path.takeIf { candidate.startsWith(root) }
}

private fun preferenceKey(name: String, type: ExpectedType): Preferences.Key<*> = when (type) {
    ExpectedType.BOOLEAN -> booleanPreferencesKey(name)
    ExpectedType.INT -> intPreferencesKey(name)
    ExpectedType.LONG -> longPreferencesKey(name)
    ExpectedType.FLOAT -> floatPreferencesKey(name)
    ExpectedType.STRING -> stringPreferencesKey(name)
    ExpectedType.STRING_SET -> stringSetPreferencesKey(name)
}

/**
 * Reads `value` under its declared type, rejecting anything that isn't actually that JSON
 * type -- org.json's own `getInt`/`getDouble`/etc. happily coerce a numeric-looking string into
 * a number, which is exactly the kind of leniency a hostile file would lean on, so this goes
 * through [JSONObject.opt] and an explicit Kotlin cast instead.
 */
private fun readTypedValue(entry: JSONObject, type: ExpectedType): Any? {
    if (!entry.has("value")) return null
    return when (type) {
        ExpectedType.BOOLEAN -> entry.opt("value") as? Boolean
        ExpectedType.INT -> (entry.opt("value") as? Number)?.toLong()
            ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        ExpectedType.LONG -> (entry.opt("value") as? Number)?.toLong()
        ExpectedType.FLOAT -> (entry.opt("value") as? Number)?.toDouble()?.toFloat()?.takeIf { it.isFinite() }
        ExpectedType.STRING -> (entry.opt("value") as? String)?.takeIf { it.length <= MAX_STRING_VALUE_LENGTH }
        ExpectedType.STRING_SET -> readStringSet(entry.opt("value") as? JSONArray)
    }
}

private fun readStringSet(array: JSONArray?): Set<String>? {
    if (array == null || array.length() > MAX_STRING_SET_ELEMENTS) return null
    val set = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val element = array.opt(i) as? String ?: return null
        if (element.length > MAX_STRING_SET_ELEMENT_LENGTH) return null
        set.add(element)
    }
    return set
}

/**
 * [Folder.kt]'s and [Prefs.jsonToMap]'s own parsers already fail soft -- an empty list or map,
 * never a throw -- on a garbage value, so storing one wouldn't crash anything on read. But it
 * would still silently wipe every real folder or override the moment a corrupted value got
 * imported, rather than simply not gaining whatever the file intended, so this keeps a
 * malformed one of these four out of the store in the first place.
 *
 * `icon_overrides_json` gets a second pass beyond "is it a JSON object of strings": each value
 * is filtered down to the two shapes the icon picker itself ever writes. See
 * [isAllowedIconOverrideValue] for why -- a value in this map reaches
 * `ContentResolver.openInputStream` directly at render time.
 */
private fun sanitizeJsonStringValue(name: String, value: String): String? = when {
    value.isBlank() -> value
    name == "folders_json" -> value.takeIf { runCatching { JSONArray(it) }.isSuccess }
    name == "icon_overrides_json" -> sanitizeIconOverrides(value)
    else -> value.takeIf { runCatching { JSONObject(it) }.isSuccess }
}

private fun sanitizeIconOverrides(value: String): String? {
    val obj = runCatching { JSONObject(value) }.getOrNull() ?: return null
    val filtered = JSONObject()
    obj.keys().forEach { key ->
        val overrideValue = obj.opt(key) as? String ?: return@forEach
        if (isAllowedIconOverrideValue(overrideValue)) filtered.put(key, overrideValue)
    }
    return filtered.toString()
}

/**
 * The icon picker only ever writes one of two shapes (`AppIcon.kt`): an icon-pack reference, or
 * a gallery `content://` URI the app was granted a persistable read permission for at pick
 * time. An imported override skips that grant entirely -- it's just a string a file handed us
 * -- so `decodeIconOverride`'s `content://` branch reaching `ContentResolver.openInputStream`
 * with a URI we hold no permission for is expected and already handled (it throws
 * `SecurityException`, caught by the `runCatching` around that call, and falls back to the
 * app/pack icon). The actual risk is `ContentResolver` treating a `file://` URI as a plain
 * filesystem path with no permission-grant check at all: normal Android file permissions still
 * apply, so this can only ever reach files this app could already read on its own (its own
 * private storage; nothing else, since it holds no storage permission), but there is no reason
 * to accept that shape from an imported file when it was never one the app itself would write.
 * Restricting to the two documented shapes closes that off, along with `javascript:`, `data:`,
 * and anything else, without needing to reason about each scheme individually.
 */
internal fun isAllowedIconOverrideValue(value: String): Boolean {
    if (value.startsWith(ICON_PACK_OVERRIDE_PREFIX)) {
        val body = value.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) return false
        return isSafeToken(body.substring(0, split)) && isSafeToken(body.substring(split + 1))
    }
    // A content URI legitimately contains '/', so it only gets the control-character check --
    // the no-separator rule above is specific to the pack: pieces, which are names, not paths.
    return value.startsWith("content://") && value.none { it.isISOControl() }
}

/**
 * This becomes a package/resource name lookup or a URI, never a file path, but a hostile file
 * has no reason to respect that -- so no path separators, no `..`, and no control characters
 * (which would include a newline smuggling a second value in).
 */
private fun isSafeToken(token: String): Boolean =
    token.isNotBlank() && "/" !in token && "\\" !in token && ".." !in token && token.none { it.isISOControl() }
