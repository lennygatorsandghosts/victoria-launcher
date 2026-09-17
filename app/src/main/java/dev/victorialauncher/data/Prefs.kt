// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

enum class EdgeSide { LEFT, RIGHT, BOTH }

/** Which edge favorites and app rows line up against. */
enum class HomeAlignment { LEFT, CENTER, RIGHT }

/** Which side of an app's name its icon sits on. */
enum class IconSide { LEFT, RIGHT }

/** The two swipe directions under the favorites that can launch an app. */
enum class QuickLaunchSlot { LEFT, RIGHT }
enum class AppFont { SYSTEM, SANS_SERIF, SERIF, MONOSPACE, CUSTOM }

/** AUTO picks light or dark text from the wallpaper's own colors. */
enum class TextColorMode { AUTO, LIGHT, DARK, MATERIAL, CUSTOM }

/**
 * The mask an icon is drawn through.
 *
 * SYSTEM leaves the drawable alone, which means whatever shape the device already applies.
 * The rest re-mask an adaptive icon's own layers; an icon that is not adaptive has no safe
 * zone to cut into, so it is left as it is whatever this says.
 */
enum class IconShape { SYSTEM, CIRCLE, ROUNDED, SQUARE }

/** When the A-Z strip shows on the home screen itself. */
enum class AzStripVisibility { NEVER, LANDSCAPE, ALWAYS }

private val Context.dataStore by preferencesDataStore(name = "victoria_prefs")

/**
 * Bumped only if the shape of an exported file changes, so an old one can be refused.
 * Not `private`: [parseSettingsExport] (ImportValidation.kt) checks it too, and a unit test
 * exercises that check directly.
 */
internal const val EXPORT_FORMAT = 1

/** Shorthand for building [Prefs.importAllowList] -- see [KnownPreference]. */
private fun bool() = KnownPreference(ExpectedType.BOOLEAN)
private fun string() = KnownPreference(ExpectedType.STRING)
private fun stringSet() = KnownPreference(ExpectedType.STRING_SET)
private fun int(min: Int, max: Int) = KnownPreference(ExpectedType.INT, NumericRange.OfInt(min, max))
private fun float(min: Float, max: Float) = KnownPreference(ExpectedType.FLOAT, NumericRange.OfFloat(min, max))

class Prefs(private val context: Context) {

    /**
     * Not `private`: [importAllowList] below is built off it directly, and a unit test walks
     * it reflectively to make sure that list never falls out of sync with a key someone adds
     * here and forgets to also allow-list -- see the comment on [importAllowList].
     */
    internal object Keys {
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val FAVORITES = stringPreferencesKey("favorites_order")
        val FOLDERS = stringPreferencesKey("folders_json")
        val NAME_OVERRIDES = stringPreferencesKey("name_overrides_json")
        val ICON_OVERRIDES = stringPreferencesKey("icon_overrides_json")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val LABEL_SIZE_SP = intPreferencesKey("label_size_sp")
        val ITEM_SPACING_DP = intPreferencesKey("item_spacing_dp")
        val SIDE_PADDING_DP = intPreferencesKey("side_padding_dp")
        val NOW_PLAYING_HEIGHT_DP = intPreferencesKey("now_playing_height_dp")
        val NOW_PLAYING_PAD_TOP = intPreferencesKey("now_playing_pad_top")
        val NOW_PLAYING_PAD_BOTTOM = intPreferencesKey("now_playing_pad_bottom")
        val WIDGET_PAD_TOP = intPreferencesKey("widget_pad_top")
        val WIDGET_PAD_BOTTOM = intPreferencesKey("widget_pad_bottom")
        val FAVORITES_PAD_TOP = intPreferencesKey("favorites_pad_top")
        val FAVORITES_PAD_BOTTOM = intPreferencesKey("favorites_pad_bottom")
        val FONT = stringPreferencesKey("font")
        val FONT_FILE = stringPreferencesKey("font_file")
        val TEXT_COLOR_CUSTOM = intPreferencesKey("text_color_custom")
        val DIM_COLOR = intPreferencesKey("dim_color")
        val ALLOW_ROTATION = booleanPreferencesKey("allow_rotation")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val HIDE_STATUS_BAR_APPLIST = booleanPreferencesKey("hide_status_bar_applist")
        val DIM_WALLPAPER_ALPHA = floatPreferencesKey("dim_wallpaper_alpha")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DIM_HOME_ALPHA = floatPreferencesKey("dim_home_alpha")
        val SHOW_FAVORITE_LABELS = booleanPreferencesKey("show_favorite_labels")
        val TEXT_COLOR_MODE = stringPreferencesKey("text_color_mode")
        val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("double_tap_to_lock")
        val EDGE_SIDE = stringPreferencesKey("edge_side")
        val ALWAYS_SHOW_AZ = booleanPreferencesKey("always_show_az")
        val AZ_STRIP_VISIBILITY = stringPreferencesKey("az_strip_visibility")
        val SHOW_ALPHABET = booleanPreferencesKey("show_alphabet")
        val ALIGN_RIGHT = booleanPreferencesKey("align_right")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
        val NOW_PLAYING_ENABLED = booleanPreferencesKey("now_playing_enabled")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val WIDGET_POSITION = intPreferencesKey("widget_position")
        val WIDGET_HEIGHT_DP = intPreferencesKey("widget_height_dp")
        val WIDGET_IDS = stringPreferencesKey("widget_ids")
        val WIDGET_SIDE_PADDING_DP = intPreferencesKey("widget_side_padding_dp")
        val WIDGET_OFFSET_X_DP = intPreferencesKey("widget_offset_x_dp")
        val SWIPE_UP_OPENS_LIST = booleanPreferencesKey("swipe_up_opens_list")
        val APPLIST_SEARCH_ENABLED = booleanPreferencesKey("applist_search_enabled")
        val APPLIST_SEARCH_BOTTOM = booleanPreferencesKey("applist_search_bottom")
        val APPLIST_SEARCH_HIDDEN = booleanPreferencesKey("applist_search_hidden")
        val SORT_BY_USAGE = booleanPreferencesKey("sort_by_usage")
        val LAUNCH_COUNTS = stringPreferencesKey("launch_counts_json")
        val EDGE_ZONE_WIDTH_DP = intPreferencesKey("edge_zone_width_dp")
        val QUICK_LAUNCH_LEFT = stringPreferencesKey("quick_launch_left_key")
        val QUICK_LAUNCH_RIGHT = stringPreferencesKey("quick_launch_right_key")
        val LAYOUT_DEFAULTS_VERSION = intPreferencesKey("layout_defaults_version")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")
        val ALIGNMENT = stringPreferencesKey("alignment")
        val APPLIST_ALIGNMENT = stringPreferencesKey("applist_alignment")
        val ICON_SIDE = stringPreferencesKey("icon_side")
        val STATUS_BAR_PEEK_SECONDS = intPreferencesKey("status_bar_peek_seconds")
        val AZ_BAND_TOP_FRACTION = floatPreferencesKey("az_band_top_fraction")
        val AZ_BAND_HEIGHT_FRACTION = floatPreferencesKey("az_band_height_fraction")
    }

    companion object {
        /**
         * The allow-list [importJson] restores against: every real preference name, the type
         * it's declared with above, and -- for INT/FLOAT -- the semantic range a legitimate
         * value has to sit inside (SEC-M2), sourced from whichever slider or stepper in the UI
         * actually sets that preference (cited per entry below) so the range isn't guessed.
         * Written out by hand rather than derived from [Keys] at runtime, because a
         * `Preferences.Key<T>`'s `T` is erased -- there is no way to ask an existing key
         * instance for its own type tag. Kept honest by two `ImportValidationTest` checks: one
         * walks [Keys] via reflection and fails if a name is missing here, and the other fails
         * if an INT/FLOAT entry has no declared range -- so an added-and-forgotten key, or a
         * numeric one added without thinking through its range, shows up as a test failure
         * rather than as a setting that silently stops restoring or a value that crashes
         * Compose later.
         */
        internal val importAllowList: Map<String, KnownPreference> = mapOf(
            Keys.HIDDEN_APPS.name to stringSet(),
            Keys.FAVORITES.name to string(),
            Keys.FOLDERS.name to string(),
            Keys.NAME_OVERRIDES.name to string(),
            Keys.ICON_OVERRIDES.name to string(),
            Keys.ICON_SIZE_DP.name to int(32, 96), // SettingsScreen icon-size slider
            Keys.LABEL_SIZE_SP.name to int(10, 28), // SettingsScreen text-size slider
            Keys.ITEM_SPACING_DP.name to int(0, 40), // SettingsScreen favorite-spacing slider
            Keys.SIDE_PADDING_DP.name to int(0, 96), // HomeScreen side-padding stepper
            Keys.NOW_PLAYING_HEIGHT_DP.name to int(48, 220), // HomeScreen now-playing-height stepper
            Keys.NOW_PLAYING_PAD_TOP.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.NOW_PLAYING_PAD_BOTTOM.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.WIDGET_PAD_TOP.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.WIDGET_PAD_BOTTOM.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.FAVORITES_PAD_TOP.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.FAVORITES_PAD_BOTTOM.name to int(0, 400), // EditLayoutControls.PADDING_RANGE
            Keys.FONT.name to string(),
            Keys.FONT_FILE.name to string(),
            // An ARGB color legitimately spans the whole Int range (the sign bit is just the
            // alpha byte), so there is no narrower range to enforce beyond the machine range
            // readTypedValue() already checks -- declared explicitly so it isn't mistaken for a
            // numeric key nobody thought about.
            Keys.TEXT_COLOR_CUSTOM.name to int(Int.MIN_VALUE, Int.MAX_VALUE),
            Keys.DIM_COLOR.name to int(Int.MIN_VALUE, Int.MAX_VALUE),
            Keys.ALLOW_ROTATION.name to bool(),
            Keys.THEMED_ICONS.name to bool(),
            Keys.ICON_SHAPE.name to string(),
            Keys.HIDE_STATUS_BAR.name to bool(),
            Keys.HIDE_STATUS_BAR_APPLIST.name to bool(),
            Keys.DIM_WALLPAPER_ALPHA.name to float(0f, 0.85f), // SettingsScreen dim-applist slider
            Keys.HAPTICS_ENABLED.name to bool(),
            Keys.DIM_HOME_ALPHA.name to float(0f, 0.85f), // SettingsScreen dim-home slider
            Keys.SHOW_FAVORITE_LABELS.name to bool(),
            Keys.TEXT_COLOR_MODE.name to string(),
            Keys.DOUBLE_TAP_TO_LOCK.name to bool(),
            Keys.EDGE_SIDE.name to string(),
            Keys.ALWAYS_SHOW_AZ.name to bool(),
            Keys.AZ_STRIP_VISIBILITY.name to string(),
            Keys.SHOW_ALPHABET.name to bool(),
            Keys.ALIGN_RIGHT.name to bool(),
            Keys.ICON_PACK_PACKAGE.name to string(),
            Keys.NOW_PLAYING_ENABLED.name to bool(),
            // AppWidgetManager assigns the real IDs; -1 is its own INVALID_APPWIDGET_ID
            // sentinel for "no widget". No UI control writes this directly, so the bound is
            // generous on purpose rather than guessed tight.
            Keys.WIDGET_ID.name to int(-1, Int.MAX_VALUE),
            // Index into the merged home list; HomeScreen.kt already coerces it to the
            // favorites count at read time (`widgetPosition.coerceIn(0, favorites.size)`), so
            // this is a sanity cap against nonsense in the file itself, not the real bound.
            Keys.WIDGET_POSITION.name to int(0, 100_000),
            Keys.WIDGET_HEIGHT_DP.name to int(80, 900), // HomeScreen widget-height stepper
            Keys.WIDGET_IDS.name to string(),
            Keys.WIDGET_SIDE_PADDING_DP.name to int(0, 96), // HomeScreen widget-side-padding stepper
            Keys.WIDGET_OFFSET_X_DP.name to int(-200, 200), // HomeScreen widget-offset stepper; matches the coerceIn already in widgetOffsetXDp/setWidgetOffsetXDp below
            Keys.SWIPE_UP_OPENS_LIST.name to bool(),
            Keys.APPLIST_SEARCH_ENABLED.name to bool(),
            Keys.APPLIST_SEARCH_BOTTOM.name to bool(),
            Keys.APPLIST_SEARCH_HIDDEN.name to bool(),
            Keys.SORT_BY_USAGE.name to bool(),
            Keys.LAUNCH_COUNTS.name to string(),
            Keys.EDGE_ZONE_WIDTH_DP.name to int(32, 96), // SettingsScreen edge-zone-width slider
            Keys.QUICK_LAUNCH_LEFT.name to string(),
            Keys.QUICK_LAUNCH_RIGHT.name to string(),
            // 0 = installed before this scheme, 1 = a genuine first run -- see ensureInstallMarker().
            Keys.LAYOUT_DEFAULTS_VERSION.name to int(0, 1),
            Keys.WELCOME_SEEN.name to bool(),
            Keys.SHOW_APP_ICONS.name to bool(),
            Keys.ALIGNMENT.name to string(),
            Keys.APPLIST_ALIGNMENT.name to string(),
            Keys.ICON_SIDE.name to string(),
            Keys.STATUS_BAR_PEEK_SECONDS.name to int(1, 30), // SettingsScreen status-bar-timeout slider
            // BandEditOverlay clamps topPx to [0, viewportPx] and heightPx to
            // [minHeightPx, viewportPx] (BandEditOverlay.kt), so both fractions of the
            // viewport land in 0f..1f.
            Keys.AZ_BAND_TOP_FRACTION.name to float(0f, 1f),
            Keys.AZ_BAND_HEIGHT_FRACTION.name to float(0f, 1f),
        )
    }

    /** Every padding a user can set by hand; their presence is what retires the first-run layout. */
    private val padKeys = listOf(
        Keys.NOW_PLAYING_PAD_TOP,
        Keys.NOW_PLAYING_PAD_BOTTOM,
        Keys.WIDGET_PAD_TOP,
        Keys.WIDGET_PAD_BOTTOM,
        Keys.FAVORITES_PAD_TOP,
        Keys.FAVORITES_PAD_BOTTOM,
    )

    private val data get() = context.dataStore.data

    /**
     * Every stored setting as JSON, for moving a set-up to another phone or keeping it.
     *
     * Written from the store itself rather than a list of keys kept alongside it, because a
     * list like that is only right until the next setting is added and nobody remembers it.
     * The type travels with each value, since JSON cannot tell 1 from 1L from 1.0f and the
     * store very much can.
     *
     * A custom font is a path into this app's own storage, so it points at nothing on another
     * phone; the font falls back to the default there until one is picked again.
     */
    suspend fun exportJson(): String {
        val stored = data.first()
        val values = JSONObject()
        stored.asMap().forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is String -> entry.put("type", "string").put("value", value)
                is Set<*> -> entry.put("type", "stringSet")
                    .put("value", JSONArray().apply { value.forEach { put(it.toString()) } })
                else -> return@forEach
            }
            values.put(key.name, entry)
        }
        return JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("app", "Victoria Launcher")
            .put("values", values)
            .toString(2)
    }

    /**
     * Replaces every setting with the ones in [text]. Returns false if it is not recognizable
     * as ours, or if nothing in it could be trusted enough to write.
     *
     * Parsing and every validation step happen in [parseSettingsExport] -- entirely before
     * this touches the store, for the same reason as before: a half-read file that had already
     * cleared the store would leave someone with neither their old set-up nor the one they were
     * restoring. Pulling that out into a top-level pure function is what makes it possible to
     * unit-test the hostile-input handling (oversize files, wrong types, malformed embedded
     * JSON, deliberately nested garbage) under a plain JVM test, with no DataStore or Context
     * needed, against `org.json` the same way the rest of this file already does. `context.filesDir`
     * is the one piece [parseSettingsExport] cannot get for itself, needed only to check a
     * `font_file` value resolves inside it (CR-11).
     *
     * A name in the file that isn't a preference this build declares is dropped rather than
     * failing the import -- see [Keys]/[importAllowList] -- and every key absent from the file
     * entirely is still cleared by `store.clear()` below, unchanged from before: an import is a
     * restore, not a merge, and a file that omits a setting is choosing its default, not asking
     * to keep whatever this device already had.
     */
    suspend fun importJson(text: String): Boolean {
        val parsed = parseSettingsExport(text, importAllowList, context.filesDir) ?: return false

        context.dataStore.edit { store ->
            store.clear()
            parsed.values.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                store[key as Preferences.Key<Any>] = value
            }
        }
        return true
    }

    /** Favorites are stored as one newline-joined string; these are the only two readers. */
    private fun readFavorites(pref: Preferences): List<String> =
        pref[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.writeFavorites(list: List<String>) {
        this[Keys.FAVORITES] = list.joinToString("\n")
    }

    /**
     * Absent means this install predates multiple widgets, so the lone [Keys.WIDGET_ID] is
     * promoted. An empty string is different: it means every widget was removed, and must not
     * resurrect that old key.
     */
    private fun readWidgetIds(pref: Preferences): List<Int> {
        val raw = pref[Keys.WIDGET_IDS]
            ?: return listOfNotNull(pref[Keys.WIDGET_ID]?.takeIf { it > 0 })
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    }

    private fun MutablePreferences.writeWidgetIds(ids: List<Int>) {
        this[Keys.WIDGET_IDS] = ids.joinToString(",")
        // Mirrored so a downgrade to a single-widget build still finds one.
        this[Keys.WIDGET_ID] = ids.firstOrNull() ?: -1
    }

    val hiddenApps: Flow<Set<String>> =
        data.map { it[Keys.HIDDEN_APPS] ?: emptySet() }.distinctUntilChanged()

    val favorites: Flow<List<String>> =
        data.map { pref -> readFavorites(pref) }.distinctUntilChanged()

    val folders: Flow<List<Folder>> =
        data.map { pref -> foldersFromJson(pref[Keys.FOLDERS]) }.distinctUntilChanged()

    val nameOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.NAME_OVERRIDES]) }.distinctUntilChanged()

    val iconOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.ICON_OVERRIDES]) }.distinctUntilChanged()

    // icon_size_dp / label_size_sp / side_padding_dp are clamped here the same way
    // widgetOffsetXDp already was, below -- a value stored on the device before this range was
    // enforced at import (SEC-M2) would otherwise reach Compose unchecked: a huge icon size can
    // overflow a layout constraint, and side padding reaches `Modifier.padding` directly in
    // several places in HomeScreen.kt, which throws on a negative value. The bounds match the
    // ones `Prefs.importAllowList` enforces on the way in.

    val iconSizeDp: Flow<Int> = data.map { (it[Keys.ICON_SIZE_DP] ?: 56).coerceIn(32, 96) }.distinctUntilChanged()

    val labelSizeSp: Flow<Int> = data.map { (it[Keys.LABEL_SIZE_SP] ?: 16).coerceIn(10, 28) }.distinctUntilChanged()

    /** Vertical gap between favorite rows. */
    val itemSpacingDp: Flow<Int> = data.map { it[Keys.ITEM_SPACING_DP] ?: 10 }.distinctUntilChanged()

    /** Left/right inset applied to every element on the home screen, so they stay in line. */
    val sidePaddingDp: Flow<Int> =
        data.map { (it[Keys.SIDE_PADDING_DP] ?: 20).coerceIn(0, 96) }.distinctUntilChanged()

    val nowPlayingHeightDp: Flow<Int> = data.map { it[Keys.NOW_PLAYING_HEIGHT_DP] ?: 64 }.distinctUntilChanged()

    /** Draggable top/bottom padding for each home block, set in edit mode. */
    val homePaddings: Flow<HomePaddings> = data.map {
        HomePaddings(
            nowPlayingTop = it[Keys.NOW_PLAYING_PAD_TOP] ?: 8,
            nowPlayingBottom = it[Keys.NOW_PLAYING_PAD_BOTTOM] ?: 8,
            widgetTop = it[Keys.WIDGET_PAD_TOP] ?: 8,
            widgetBottom = it[Keys.WIDGET_PAD_BOTTOM] ?: 8,
            favoritesTop = it[Keys.FAVORITES_PAD_TOP] ?: 8,
            favoritesBottom = it[Keys.FAVORITES_PAD_BOTTOM] ?: 24,
        )
    }.distinctUntilChanged()

    /** Absolute path of the typeface the user supplied, once it has been copied in. */
    val fontFile: Flow<String?> = data.map { it[Keys.FONT_FILE] }.distinctUntilChanged()

    /**
     * Whether the launcher turns with the device.
     *
     * Null until it is set, which leaves the decision to the screen: a tablet has the height
     * for this sideways and a phone does not. Someone who wants it anyway — a phone in a car
     * mount is the case that came up — can say so.
     */
    val allowRotation: Flow<Boolean?> = data.map { it[Keys.ALLOW_ROTATION] }.distinctUntilChanged()

    /**
     * What the wallpaper is dimmed with, as an opaque RGB; the dim sliders set how much of it
     * lands. Black until the user picks otherwise, which is what it always was.
     */
    val dimColor: Flow<Int> =
        data.map { it[Keys.DIM_COLOR] ?: 0xFF000000.toInt() }.distinctUntilChanged()

    /** Used when the text color mode is CUSTOM. Opaque white until the user picks something. */
    val textColorCustom: Flow<Int> =
        data.map { it[Keys.TEXT_COLOR_CUSTOM] ?: 0xFFFFFFFF.toInt() }.distinctUntilChanged()

    /**
     * Draws the monochrome layer Android 13 added to adaptive icons, tinted to the system
     * palette, instead of the app's own colors. Apps that ship no such layer keep their
     * ordinary icon — there is nothing to derive one from.
     */
    val themedIcons: Flow<Boolean> = data.map { it[Keys.THEMED_ICONS] ?: false }.distinctUntilChanged()

    val iconShape: Flow<IconShape> = data.map { pref ->
        pref[Keys.ICON_SHAPE]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: IconShape.SYSTEM
    }.distinctUntilChanged()

    val font: Flow<AppFont> = data.map {
        runCatching { AppFont.valueOf(it[Keys.FONT] ?: AppFont.SYSTEM.name) }.getOrDefault(AppFont.SYSTEM)
    }.distinctUntilChanged()

    val hideStatusBar: Flow<Boolean> = data.map { it[Keys.HIDE_STATUS_BAR] ?: false }.distinctUntilChanged()

    /**
     * The same choice for the app list, which used to follow the home screen's.
     *
     * Falls back to it until set, so nobody's status bar appears where it did not before.
     */
    val hideStatusBarAppList: Flow<Boolean> =
        data.map { it[Keys.HIDE_STATUS_BAR_APPLIST] ?: it[Keys.HIDE_STATUS_BAR] ?: false }
            .distinctUntilChanged()

    val dimWallpaperAlpha: Flow<Float> = data.map { it[Keys.DIM_WALLPAPER_ALPHA] ?: 0.35f }.distinctUntilChanged()

    val hapticsEnabled: Flow<Boolean> = data.map { it[Keys.HAPTICS_ENABLED] ?: true }.distinctUntilChanged()

    val dimHomeAlpha: Flow<Float> = data.map { it[Keys.DIM_HOME_ALPHA] ?: 0f }.distinctUntilChanged()

    val showFavoriteLabels: Flow<Boolean> =
        data.map { it[Keys.SHOW_FAVORITE_LABELS] ?: true }.distinctUntilChanged()

    val textColorMode: Flow<TextColorMode> = data.map {
        runCatching { TextColorMode.valueOf(it[Keys.TEXT_COLOR_MODE] ?: TextColorMode.AUTO.name) }
            .getOrDefault(TextColorMode.AUTO)
    }.distinctUntilChanged()

    val doubleTapToLock: Flow<Boolean> =
        data.map { it[Keys.DOUBLE_TAP_TO_LOCK] ?: false }.distinctUntilChanged()

    val edgeSide: Flow<EdgeSide> = data.map {
        runCatching { EdgeSide.valueOf(it[Keys.EDGE_SIDE] ?: EdgeSide.RIGHT.name) }.getOrDefault(EdgeSide.RIGHT)
    }.distinctUntilChanged()

    /** Keep the A-Z strip on screen even when the app list is closed. */
    /**
     * When the strip sits on the home screen with no app list open.
     *
     * Reads the old on/off answer when this has not been set, so nobody's strip appears or
     * disappears on update. LANDSCAPE is for a phone in a car mount, where the strip earns
     * the room it takes and upright it does not.
     */
    val azStripVisibility: Flow<AzStripVisibility> = data.map { pref ->
        pref[Keys.AZ_STRIP_VISIBILITY]?.let { runCatching { AzStripVisibility.valueOf(it) }.getOrNull() }
            ?: if (pref[Keys.ALWAYS_SHOW_AZ] == true) AzStripVisibility.ALWAYS else AzStripVisibility.NEVER
    }.distinctUntilChanged()

    /** The A-Z strip inside the app list; the edge gesture still works without it. */
    val showAlphabet: Flow<Boolean> = data.map { it[Keys.SHOW_ALPHABET] ?: true }.distinctUntilChanged()

    /** Lay icons and labels out from the right edge instead of the left. */
    val alignRight: Flow<Boolean> = data.map { it[Keys.ALIGN_RIGHT] ?: false }.distinctUntilChanged()

    val iconPackPackage: Flow<String?> = data.map { it[Keys.ICON_PACK_PACKAGE] }.distinctUntilChanged()

    val nowPlayingEnabled: Flow<Boolean> = data.map { it[Keys.NOW_PLAYING_ENABLED] ?: false }.distinctUntilChanged()

    val widgetId: Flow<Int> = data.map { it[Keys.WIDGET_ID] ?: -1 }.distinctUntilChanged()
    /** Index into the merged (favorites + widget) home list where the widget sits. 0 = top. */
    val widgetPosition: Flow<Int> = data.map { it[Keys.WIDGET_POSITION] ?: 0 }.distinctUntilChanged()
    val widgetHeightDp: Flow<Int> = data.map { it[Keys.WIDGET_HEIGHT_DP] ?: 180 }.distinctUntilChanged()

    val widgetIds: Flow<List<Int>> = data.map { readWidgetIds(it) }.distinctUntilChanged()

    /**
     * The widget's own left/right inset. Absent means it has never been set apart, so it
     * follows the favorites and nothing moves the first time this key appears.
     *
     * Clamped the same way [sidePaddingDp] is (SEC-M2): HomeScreen.kt reaches
     * `Modifier.padding(horizontal = widgetSidePaddingDp.dp)` directly, which throws on negative.
     */
    val widgetSidePaddingDp: Flow<Int> =
        data.map { (it[Keys.WIDGET_SIDE_PADDING_DP] ?: it[Keys.SIDE_PADDING_DP] ?: 20).coerceIn(0, 96) }
            .distinctUntilChanged()

    /**
     * How far the widget is shifted sideways, negative left and positive right.
     *
     * A third-party widget lays out its own contents and most clocks center theirs, which
     * nothing out here can reach inside. Moving the whole widget is what moves them. Side
     * padding already sets how wide it is; this is only where that width sits.
     */
    val widgetOffsetXDp: Flow<Int> =
        data.map { (it[Keys.WIDGET_OFFSET_X_DP] ?: 0).coerceIn(-200, 200) }.distinctUntilChanged()

    /** Opening the app list by swiping up from the home screen. */
    val swipeUpOpensList: Flow<Boolean> =
        data.map { it[Keys.SWIPE_UP_OPENS_LIST] ?: false }.distinctUntilChanged()

    val appListSearchEnabled: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_ENABLED] ?: false }.distinctUntilChanged()

    /** Within thumb reach on a tall phone, rather than up by the status bar. */
    val appListSearchBottom: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_BOTTOM] ?: false }.distinctUntilChanged()

    /**
     * Lets a search reach apps hidden from the list.
     *
     * Off by default: hidden means hidden, and someone who hid an app did not ask for it back.
     */
    val appListSearchHidden: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_HIDDEN] ?: false }.distinctUntilChanged()

    /** Orders each letter's apps by how often they were opened from here, rather than by name. */
    val sortByUsage: Flow<Boolean> = data.map { it[Keys.SORT_BY_USAGE] ?: false }.distinctUntilChanged()

    val launchCounts: Flow<Map<String, Int>> = data.map { pref ->
        jsonToMap(pref[Keys.LAUNCH_COUNTS]).mapValues { (_, v) -> v.toIntOrNull() ?: 0 }
    }.distinctUntilChanged()

    /** Width of the invisible strip at each screen edge that opens the app list. */
    val edgeZoneWidthDp: Flow<Int> = data.map { it[Keys.EDGE_ZONE_WIDTH_DP] ?: 56 }.distinctUntilChanged()

    val quickLaunchLeft: Flow<String?> = data.map { it[Keys.QUICK_LAUNCH_LEFT] }.distinctUntilChanged()

    val quickLaunchRight: Flow<String?> = data.map { it[Keys.QUICK_LAUNCH_RIGHT] }.distinctUntilChanged()

    /** Drawing icons at all; off leaves text-only rows everywhere. */
    val showAppIcons: Flow<Boolean> = data.map { it[Keys.SHOW_APP_ICONS] ?: true }.distinctUntilChanged()

    /**
     * Reads [key], falling back to the shared alignment and then to the old right-handed
     * boolean, so an upgrade keeps whichever side the user had chosen and splitting the two
     * lists apart starts them both where they already were.
     */
    private fun readAlignment(pref: Preferences, key: Preferences.Key<String>): HomeAlignment {
        val stored = pref[key] ?: pref[Keys.ALIGNMENT]
        return when {
            stored != null -> runCatching { HomeAlignment.valueOf(stored) }.getOrDefault(HomeAlignment.LEFT)
            pref[Keys.ALIGN_RIGHT] == true -> HomeAlignment.RIGHT
            else -> HomeAlignment.LEFT
        }
    }

    /** How the favorites on the home screen line up. */
    val alignment: Flow<HomeAlignment> =
        data.map { readAlignment(it, Keys.ALIGNMENT) }.distinctUntilChanged()

    /** How the A-Z list lines up, which people want to differ from the home screen. */
    val appListAlignment: Flow<HomeAlignment> =
        data.map { readAlignment(it, Keys.APPLIST_ALIGNMENT) }.distinctUntilChanged()

    val iconSide: Flow<IconSide> = data.map {
        runCatching { IconSide.valueOf(it[Keys.ICON_SIDE] ?: IconSide.LEFT.name) }
            .getOrDefault(IconSide.LEFT)
    }.distinctUntilChanged()

    /** How long a pull-down keeps the status bar on screen before it fades away again. */
    val statusBarPeekSeconds: Flow<Int> =
        data.map { it[Keys.STATUS_BAR_PEEK_SECONDS] ?: 5 }.distinctUntilChanged()

    /** Null while the A-Z strip follows the favorites; a set range is a fraction of the viewport. */
    val scrubBand: Flow<Pair<Float, Float>?> = data.map { pref ->
        val top = pref[Keys.AZ_BAND_TOP_FRACTION]
        val height = pref[Keys.AZ_BAND_HEIGHT_FRACTION]
        if (top != null && height != null) top to height else null
    }.distinctUntilChanged()

    /** 0 = installed before this scheme, 1 = a genuine first run. Absent until [ensureInstallMarker]. */
    val layoutDefaultsVersion: Flow<Int?> =
        data.map { it[Keys.LAYOUT_DEFAULTS_VERSION] }.distinctUntilChanged()

    val welcomeSeen: Flow<Boolean> = data.map { it[Keys.WELCOME_SEEN] ?: false }.distinctUntilChanged()

    /** True once any padding has been set by hand, which retires the computed first-run layout. */
    val hasCustomLayout: Flow<Boolean> =
        data.map { pref -> padKeys.any { pref.contains(it) } }.distinctUntilChanged()

    suspend fun setHidden(componentKey: String, hidden: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.HIDDEN_APPS] ?: emptySet()
            pref[Keys.HIDDEN_APPS] = if (hidden) current + componentKey else current - componentKey
        }
    }

    suspend fun setFavorites(list: List<String>) {
        context.dataStore.edit { it.writeFavorites(list) }
    }

    suspend fun addFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            val current = readFavorites(pref)
            if (componentKey !in current) pref.writeFavorites(current + componentKey)
        }
    }

    suspend fun removeFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            pref.writeFavorites(readFavorites(pref) - componentKey)
        }
    }

    /** Creates the folder if [id] is new, otherwise replaces it. */
    suspend fun upsertFolder(folder: Folder) {
        context.dataStore.edit { pref ->
            val existing = foldersFromJson(pref[Keys.FOLDERS])
            val updated = if (existing.any { it.id == folder.id }) {
                existing.map { if (it.id == folder.id) folder else it }
            } else {
                existing + folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /** Removes the folder and its row; the apps themselves are untouched. */
    suspend fun deleteFolder(id: String) {
        context.dataStore.edit { pref ->
            pref[Keys.FOLDERS] = foldersToJson(foldersFromJson(pref[Keys.FOLDERS]).filterNot { it.id == id })
            pref.writeFavorites(readFavorites(pref).filterNot { it == folderToken(id) })
        }
    }

    suspend fun setFolderIcon(id: String, icon: String?) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(icon = icon) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setFolderApps(id: String, apps: List<String>) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(apps = apps) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /**
     * Puts [componentKey] in the folder. An app that was a top-level favorite moves into it
     * rather than being duplicated in both places.
     */
    suspend fun addAppToFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId && componentKey !in folder.apps) {
                    folder.copy(apps = folder.apps + componentKey)
                } else {
                    folder
                }
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
            pref.writeFavorites(readFavorites(pref).filterNot { it == componentKey })
        }
    }

    suspend fun removeAppFromFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId) folder.copy(apps = folder.apps - componentKey) else folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setNameOverride(componentKey: String, name: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.NAME_OVERRIDES]).toMutableMap()
            if (name.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = name
            pref[Keys.NAME_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconOverride(componentKey: String, uri: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.ICON_OVERRIDES]).toMutableMap()
            if (uri.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = uri
            pref[Keys.ICON_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconSizeDp(v: Int) {
        context.dataStore.edit { it[Keys.ICON_SIZE_DP] = v }
    }

    suspend fun setLabelSizeSp(v: Int) {
        context.dataStore.edit { it[Keys.LABEL_SIZE_SP] = v }
    }

    suspend fun setSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.SIDE_PADDING_DP] = v }
    }

    suspend fun setItemSpacingDp(v: Int) {
        context.dataStore.edit { it[Keys.ITEM_SPACING_DP] = v }
    }

    suspend fun setNowPlayingHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_HEIGHT_DP] = v }
    }

    suspend fun setHomePadding(slot: PaddingSlot, v: Int) {
        val key = when (slot) {
            PaddingSlot.NOW_PLAYING_TOP -> Keys.NOW_PLAYING_PAD_TOP
            PaddingSlot.NOW_PLAYING_BOTTOM -> Keys.NOW_PLAYING_PAD_BOTTOM
            PaddingSlot.WIDGET_TOP -> Keys.WIDGET_PAD_TOP
            PaddingSlot.WIDGET_BOTTOM -> Keys.WIDGET_PAD_BOTTOM
            PaddingSlot.FAVORITES_TOP -> Keys.FAVORITES_PAD_TOP
            PaddingSlot.FAVORITES_BOTTOM -> Keys.FAVORITES_PAD_BOTTOM
        }
        context.dataStore.edit { it[key] = v }
    }

    suspend fun setFontFile(path: String?) {
        context.dataStore.edit { pref ->
            if (path == null) pref.remove(Keys.FONT_FILE) else pref[Keys.FONT_FILE] = path
        }
    }

    suspend fun setAllowRotation(v: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_ROTATION] = v }
    }

    suspend fun setDimColor(argb: Int) {
        context.dataStore.edit { it[Keys.DIM_COLOR] = argb }
    }

    suspend fun setTextColorCustom(argb: Int) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_CUSTOM] = argb }
    }

    suspend fun setThemedIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.THEMED_ICONS] = v }
    }

    suspend fun setIconShape(v: IconShape) {
        context.dataStore.edit { it[Keys.ICON_SHAPE] = v.name }
    }

    suspend fun setFont(f: AppFont) {
        context.dataStore.edit { it[Keys.FONT] = f.name }
    }

    suspend fun setHideStatusBarAppList(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR_APPLIST] = v }
    }

    suspend fun setHideStatusBar(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR] = v }
    }

    suspend fun setDimWallpaperAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_WALLPAPER_ALPHA] = v }
    }

    suspend fun setDimHomeAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_HOME_ALPHA] = v }
    }

    suspend fun setShowFavoriteLabels(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAVORITE_LABELS] = v }
    }

    suspend fun setTextColorMode(v: TextColorMode) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_MODE] = v.name }
    }

    suspend fun setDoubleTapToLock(v: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_TO_LOCK] = v }
    }

    suspend fun setHapticsEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = v }
    }

    suspend fun setShowAlphabet(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ALPHABET] = v }
    }

    suspend fun setAlignRight(v: Boolean) {
        context.dataStore.edit { it[Keys.ALIGN_RIGHT] = v }
    }

    suspend fun setAzStripVisibility(v: AzStripVisibility) {
        context.dataStore.edit {
            it[Keys.AZ_STRIP_VISIBILITY] = v.name
            // Kept in step so an older build reads something sensible if one is installed.
            it[Keys.ALWAYS_SHOW_AZ] = v == AzStripVisibility.ALWAYS
        }
    }

    suspend fun setEdgeSide(v: EdgeSide) {
        context.dataStore.edit { it[Keys.EDGE_SIDE] = v.name }
    }

    suspend fun setIconPackPackage(pkg: String?) {
        context.dataStore.edit { pref ->
            if (pkg.isNullOrBlank()) pref.remove(Keys.ICON_PACK_PACKAGE) else pref[Keys.ICON_PACK_PACKAGE] = pkg
        }
    }

    suspend fun setWidgetId(id: Int) {
        context.dataStore.edit { it[Keys.WIDGET_ID] = id }
    }

    suspend fun setNowPlayingEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_ENABLED] = v }
    }

    suspend fun setWidgetPosition(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_POSITION] = v }
    }

    suspend fun setWidgetHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_HEIGHT_DP] = v }
    }

    suspend fun addWidgetId(id: Int) {
        context.dataStore.edit { pref ->
            val current = readWidgetIds(pref)
            if (id !in current) pref.writeWidgetIds(current + id)
        }
    }

    suspend fun removeWidgetId(id: Int) {
        context.dataStore.edit { pref -> pref.writeWidgetIds(readWidgetIds(pref) - id) }
    }

    suspend fun setWidgetSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_SIDE_PADDING_DP] = v }
    }

    suspend fun setWidgetOffsetXDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_OFFSET_X_DP] = v.coerceIn(-200, 200) }
    }

    suspend fun setSwipeUpOpensList(v: Boolean) {
        context.dataStore.edit { it[Keys.SWIPE_UP_OPENS_LIST] = v }
    }

    suspend fun setAppListSearchEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_ENABLED] = v }
    }

    suspend fun setAppListSearchBottom(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_BOTTOM] = v }
    }

    suspend fun setAppListSearchHidden(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_HIDDEN] = v }
    }

    suspend fun setSortByUsage(v: Boolean) {
        context.dataStore.edit { it[Keys.SORT_BY_USAGE] = v }
    }

    /**
     * Counted for every launch, not just while the usage sort is on, so turning the sort on
     * later has a history to order by rather than starting from nothing.
     */
    suspend fun incrementLaunchCount(componentKey: String) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.LAUNCH_COUNTS]).toMutableMap()
            map[componentKey] = ((map[componentKey]?.toIntOrNull() ?: 0) + 1).toString()
            pref[Keys.LAUNCH_COUNTS] = mapToJson(map)
        }
    }

    suspend fun setEdgeZoneWidthDp(v: Int) {
        context.dataStore.edit { it[Keys.EDGE_ZONE_WIDTH_DP] = v }
    }

    suspend fun setQuickLaunch(slot: QuickLaunchSlot, componentKey: String?) {
        val key = when (slot) {
            QuickLaunchSlot.LEFT -> Keys.QUICK_LAUNCH_LEFT
            QuickLaunchSlot.RIGHT -> Keys.QUICK_LAUNCH_RIGHT
        }
        context.dataStore.edit { pref ->
            if (componentKey.isNullOrBlank()) pref.remove(key) else pref[key] = componentKey
        }
    }

    suspend fun setShowAppIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_APP_ICONS] = v }
    }

    suspend fun setAlignment(v: HomeAlignment) {
        context.dataStore.edit {
            it[Keys.ALIGNMENT] = v.name
            // Kept in step so a downgrade still lands on the side the user picked.
            it[Keys.ALIGN_RIGHT] = v == HomeAlignment.RIGHT
        }
    }

    suspend fun setAppListAlignment(v: HomeAlignment) {
        context.dataStore.edit { it[Keys.APPLIST_ALIGNMENT] = v.name }
    }

    suspend fun setIconSide(v: IconSide) {
        context.dataStore.edit { it[Keys.ICON_SIDE] = v.name }
    }

    suspend fun setStatusBarPeekSeconds(v: Int) {
        context.dataStore.edit { it[Keys.STATUS_BAR_PEEK_SECONDS] = v }
    }

    suspend fun setScrubBand(topFraction: Float, heightFraction: Float) {
        context.dataStore.edit {
            it[Keys.AZ_BAND_TOP_FRACTION] = topFraction
            it[Keys.AZ_BAND_HEIGHT_FRACTION] = heightFraction
        }
    }

    /** Hands the strip back to following the favorites list. */
    suspend fun clearScrubBand() {
        context.dataStore.edit {
            it.remove(Keys.AZ_BAND_TOP_FRACTION)
            it.remove(Keys.AZ_BAND_HEIGHT_FRACTION)
        }
    }

    suspend fun setWelcomeSeen(v: Boolean) {
        context.dataStore.edit { it[Keys.WELCOME_SEEN] = v }
    }

    /**
     * Stamps whether this install is new, once. Nothing writes to the store before the user
     * changes something, so an empty store is the one reliable signal of a first run — which
     * is why this has to run before any other setter can muddy it.
     */
    suspend fun ensureInstallMarker() {
        context.dataStore.edit { pref ->
            if (pref[Keys.LAYOUT_DEFAULTS_VERSION] == null) {
                pref[Keys.LAYOUT_DEFAULTS_VERSION] = if (pref.asMap().isEmpty()) 1 else 0
            }
        }
    }

    private fun jsonToMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.getString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}