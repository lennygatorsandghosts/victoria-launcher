// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.media.NotificationCountBus
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.AppRepository
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.IconShape
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-app icon overrides are stored either as a gallery `content://` URI or as `pack:<pkg>:<drawable>`. */
const val ICON_PACK_OVERRIDE_PREFIX = "pack:"

fun encodePackOverride(packPackage: String, drawableName: String) =
    "$ICON_PACK_OVERRIDE_PREFIX$packPackage:$drawableName"

/**
 * Icons are rasterised once and cached. They used to be drawn by handing a Drawable to an
 * ImageView through AndroidView, which meant inflating a real Android View per row — far too
 * expensive for a list that rebuilds while scrubbing.
 *
 * The cache is bounded by *bytes*, not entry count. A fixed count is the wrong unit here: the
 * cache key includes the rasterised pixel size, so moving the icon-size slider adds a whole
 * new generation of bitmaps rather than replacing the old one, and at 96dp on a dense screen
 * a single icon is ~450 KB. Sizing by bytes against the heap keeps that bounded no matter how
 * many apps are installed or how large the icons are set.
 */
private object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb()) {
        // Reported in KB so the running total cannot overflow an Int.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    private fun maxSizeKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 8L).toInt().coerceIn(4 * 1024, 96 * 1024)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)

    /**
     * Dropped wholesale when a package is added, removed or changed: an app that ships a new
     * icon in an update keeps none of the key's components, so nothing else would invalidate
     * the stale bitmap.
     */
    fun clear() = cache.evictAll()
}

fun clearIconCache() {
    IconCache.clear()
    AppRepository.clearPublisherMemo()
}

// The class name is in here for the rows whose key alone cannot tell two pictures apart: the
// private-space row keeps one key across locking and unlocking, and the padlock it draws is
// not the same picture either side of that. For an app it is what the key already says.
private fun iconCacheKey(
    app: AppInfo,
    iconPack: String?,
    override: String?,
    px: Int,
    style: IconStyle,
    badgeSuffix: String = "",
) = "${app.key}|${app.componentName.className}|$iconPack|$override|$px|${style.shape}|${style.themed}|${style.background}$badgeSuffix"

/** Everything about how an icon is drawn that is not the icon itself. */
@Immutable
data class IconStyle(
    val shape: IconShape,
    val themed: Boolean,
    val background: Int,
    val foreground: Int,
)

/**
 * Decode every app's icon ahead of time, off the main thread. Without this the first open of
 * the A-Z list rasterises a screenful of icons synchronously during composition, which is
 * what made it take a beat to appear.
 *
 * Work is fanned out across the dispatcher rather than run one icon at a time, and
 * [priorityKeys] (favorites and folder members) go first so the home screen is covered before
 * the long tail of everything else installed.
 */
suspend fun warmIconCache(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
    px: Int,
    style: IconStyle,
    priorityKeys: Set<String> = emptySet(),
    badges: Boolean = true,
) {
    if (px <= 0 || apps.isEmpty()) return
    val victoriaApp = context.applicationContext as VictoriaApp

    fun warm(app: AppInfo) {
        rasterise(context, victoriaApp, app, iconPack, overrides, px, style, badges)
    }

    val (first, rest) = apps.partition { it.key in priorityKeys }
    // Explicitly off the caller's dispatcher: this is invoked from a LaunchedEffect, which
    // runs on Main, and coroutineScope/async would inherit it.
    withContext(Dispatchers.Default) {
        for (group in listOf(first, rest)) {
            if (group.isEmpty()) continue
            group.chunked(CHUNK_SIZE)
                .map { chunk -> async { chunk.forEach(::warm) } }
                .awaitAll()
        }
    }
}

/** Big enough that per-task overhead stays negligible against a drawable decode. */
private const val CHUNK_SIZE = 16

/**
 * Icon pack and per-app overrides, provided once for the whole tree. Collecting these flows
 * inside AppIcon meant every visible row started two DataStore collections of its own — with
 * a screenful of rows that is dozens of disk-backed collectors spun up the moment the list
 * appears, each resolving its icon twice (once for the initial value, once for the real one).
 */
@Immutable
data class IconConfig(
    val pack: String?,
    val overrides: Map<String, String>,
    /** False draws no icons at all, for people who want the list to be nothing but names. */
    val showIcons: Boolean,
    /** Draw the monochrome layer, tinted, instead of the app's own colors. */
    val themed: Boolean,
    /** Whether an app showing notifications is badged with how many. */
    val notificationBadges: Boolean,
    val shape: IconShape,
    /** Whether a pinned shortcut carries the icon of the app it opens in, in its corner. */
    val shortcutBadges: Boolean = true,
)

val LocalIconConfig = staticCompositionLocalOf {
    IconConfig(
        pack = null,
        overrides = emptyMap(),
        showIcons = true,
        themed = false,
        notificationBadges = false,
        shape = IconShape.SYSTEM,
        shortcutBadges = true,
    )
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val config = LocalIconConfig.current
    // Nothing drawn and no space taken, so rows close up rather than leaving a hole.
    if (!config.showIcons) return
    val iconPackPackage = config.pack
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    val scheme = MaterialTheme.colorScheme
    val style = IconStyle(
        shape = config.shape,
        themed = config.themed,
        background = scheme.primaryContainer.toArgb(),
        foreground = scheme.onPrimaryContainer.toArgb(),
    )

    // The key is worked out here as well as inside rasterise so that remember can tell when the
    // picture has changed; both come from the one function, so they cannot disagree.
    val cacheKey = rasterKey(app, iconPackPackage, config.overrides, px, style, config.shortcutBadges)
    val bitmap: ImageBitmap? = remember(cacheKey) {
        rasterise(context, victoriaApp, app, iconPackPackage, config.overrides, px, style, config.shortcutBadges)
    }

    // Badged rather than drawn into the bitmap: the count changes while the icon does not, and
    // baking it in would throw away the cached icon every time a notification arrived.
    val badge = notificationBadgeCount(app)
    Box(modifier = modifier.size(sizeDp.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(sizeDp.dp),
            )
        }
        if (badge > 0) NotificationBadge(badge, sizeDp, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * How many notifications to show on this app, or zero for none.
 *
 * Zero whenever the setting is off or the listener is not connected, so nothing is drawn from
 * a count that is only as current as the last time the service was alive.
 */
@Composable
private fun notificationBadgeCount(app: AppInfo): Int {
    if (!LocalIconConfig.current.notificationBadges) return 0
    val counts by NotificationCountBus.counts.collectAsState()
    return counts[app.componentName.packageName] ?: 0
}

/**
 * The count, in a filled circle at the icon's top corner.
 *
 * Sized from the icon rather than fixed, so it stays in proportion at every icon size the
 * launcher offers. Anything past nine is drawn as 9+: the circle is the signal, and a
 * three-digit number in it is unreadable at any size an icon is drawn at.
 */
@Composable
private fun NotificationBadge(count: Int, iconSizeDp: Int, modifier: Modifier = Modifier) {
    val diameter = (iconSizeDp * 0.42f).dp.coerceIn(14.dp, 22.dp)
    Box(
        modifier = modifier
            .size(diameter)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (diameter.value * 0.55f).sp,
            lineHeight = (diameter.value * 0.55f).sp,
            maxLines = 1,
        )
    }
}

/**
 * The cache key for [app] drawn with these settings, and the styling that actually applies.
 *
 * An override or an icon pack is a picture the user chose; neither is an adaptive icon with
 * layers to tint or a safe zone to cut into, so styling only applies to what the app itself
 * supplies.
 *
 * A shortcut's key also carries whether it is badged, and a stamp of every override: its
 * badge is another row's icon, so an override set on the app it opens in changes this picture
 * without changing anything else in this key.
 */
private fun rasterKeyAndStyle(
    app: AppInfo,
    iconPackPackage: String?,
    overrides: Map<String, String>,
    px: Int,
    base: IconStyle,
    badges: Boolean,
): Pair<String, IconStyle> {
    val overrideValue = overrides[app.key]
    val styled = overrideValue == null && iconPackPackage == null
    val style = if (styled) base else base.copy(shape = IconShape.SYSTEM, themed = false)
    val isShortcut = app.kind == EntryKind.SHORTCUT
    // Only a shortcut's key needs it, and the map is hashed on every row drawn otherwise.
    val stamp = if (isShortcut && badges) overrides.hashCode() else 0
    val suffix = badgeKeySuffix(isShortcut, badges, stamp, base)
    return iconCacheKey(app, iconPackPackage, overrideValue, px, style, suffix) to style
}

private fun rasterKey(
    app: AppInfo,
    iconPackPackage: String?,
    overrides: Map<String, String>,
    px: Int,
    base: IconStyle,
    badges: Boolean,
): String = rasterKeyAndStyle(app, iconPackPackage, overrides, px, base, badges).first

/**
 * The one place an icon is decoded, styled and cached, for the rows as they are drawn and for
 * [warmIconCache] ahead of them alike, so a badge is drawn everywhere a row is and never twice.
 *
 * [base] is the style as the settings describe it, before an override or icon pack switches
 * the styling off for that one picture: a shortcut with a custom icon still has its badge
 * drawn the way that app is drawn everywhere else.
 *
 * With [badges] on, a pinned shortcut that has a picture of its own (from its publisher or a
 * custom one set on the row) carries its publisher's icon in the bottom corner, drawn by this
 * same function as that app's own row, so it follows the icon pack, a custom icon set on that
 * app, and themed icons, with no rules of its own. A shortcut without a picture of its own
 * already shows the publisher's icon, and badging it with itself would say nothing.
 */
internal fun rasterise(
    context: Context,
    victoriaApp: VictoriaApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrides: Map<String, String>,
    px: Int,
    base: IconStyle,
    badges: Boolean,
): ImageBitmap? {
    if (px <= 0) return null
    val (cacheKey, style) = rasterKeyAndStyle(app, iconPackPackage, overrides, px, base, badges)
    IconCache.get(cacheKey)?.let { return it }
    val overrideValue = overrides[app.key]
    val isShortcut = app.kind == EntryKind.SHORTCUT

    return runCatching {
        // Worked out first because it decides everything below: an icon too small to carry a
        // badge (a folder's cover cells, say) is drawn exactly as it would be with badges off,
        // system profile badge and all, rather than losing that and gaining nothing.
        val geometry = badgeGeometry(px)
        val publisher = if (badges && isShortcut && geometry != null) {
            victoriaApp.appRepository.publisherApp(app)
        } else {
            null
        }
        // Without the system's profile badge when the publisher's icon is going in: they would
        // share a corner. The publisher's icon of a work or private-space app is itself badged
        // for its profile, so nothing is lost.
        val shortcutOwn = if (publisher != null && overrideValue == null) {
            victoriaApp.appRepository.shortcutOwnIcon(app)
        } else {
            null
        }
        val hasOwnIcon = overrideValue != null || shortcutOwn != null
        val drawable = shortcutOwn ?: resolveDrawable(context, victoriaApp, app, iconPackPackage, overrideValue)
        val rendered = renderIcon(drawable, px, style.shape, style.themed, style.background, style.foreground)
        val parent = if (
            publisher != null &&
            geometry != null &&
            badgePlan(isShortcut, badges, hasPublisher = true, hasOwnIcon = hasOwnIcon) == BadgePlan.BADGE
        ) {
            rasterise(context, victoriaApp, publisher, iconPackPackage, overrides, px, base, badges = false)
                ?.asAndroidBitmap()
        } else {
            null
        }
        val finalBitmap = if (parent != null && geometry != null) compositeBadge(rendered, parent, geometry) else rendered
        finalBitmap.asImageBitmap().also { IconCache.put(cacheKey, it) }
    }.getOrNull()
}

/**
 * [badge] scaled into the bottom corner of [base], after a slightly larger copy of it is cut
 * out of [base] first. The cut leaves a thin ring of whatever is behind the icon around the
 * badge, which is what keeps it readable against a busy picture; it follows the badge's own
 * outline, so a round icon gets a round ring and a square one a square ring.
 *
 * Drawn straight onto [base]: it is the bitmap renderIcon has just allocated for this one
 * picture, mutable and not yet cached or shared with anything.
 */
private fun compositeBadge(base: Bitmap, badge: Bitmap, geometry: BadgeGeometry): Bitmap {
    val out = base
    val canvas = Canvas(out)
    val source: Rect? = null
    val flags = Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG
    val cutPaint = Paint(flags).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    canvas.drawBitmap(
        badge,
        source,
        RectF(
            geometry.haloLeft.toFloat(),
            geometry.haloTop.toFloat(),
            (geometry.haloLeft + geometry.haloSize).toFloat(),
            (geometry.haloTop + geometry.haloSize).toFloat(),
        ),
        cutPaint,
    )
    canvas.drawBitmap(
        badge,
        source,
        RectF(
            geometry.left.toFloat(),
            geometry.top.toFloat(),
            (geometry.left + geometry.size).toFloat(),
            (geometry.top + geometry.size).toFloat(),
        ),
        Paint(flags),
    )
    return out
}

/**
 * Resolves an icon override — an icon-pack drawable or a picture from the gallery — for
 * either an app or a folder. Both use the same encoding on purpose, so a folder can take any
 * icon an app can.
 */
internal fun decodeIconOverride(
    context: Context,
    victoriaApp: VictoriaApp,
    override: String?,
): Drawable? = when {
    override == null -> null

    override.startsWith(ICON_PACK_OVERRIDE_PREFIX) -> {
        val body = override.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) {
            null
        } else {
            victoriaApp.iconPackRepository.loadPackDrawable(
                body.substring(0, split),
                body.substring(split + 1),
            )
        }
    }

    else -> runCatching {
        val uri = Uri.parse(override)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }.getOrNull()
}

private fun resolveDrawable(
    context: Context,
    victoriaApp: VictoriaApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrideValue: String?,
): Drawable =
    decodeIconOverride(context, victoriaApp, overrideValue)
        ?: if (app.kind != EntryKind.APP) {
            // An icon pack is matched by component, and a shortcut carries its publisher's:
            // every bookmark would come back wearing the browser's icon instead of its own,
            // which is the one thing that tells two of them apart.
            victoriaApp.appRepository.loadIcon(app)
        } else {
            victoriaApp.iconPackRepository.getIcon(iconPackPackage, app.componentName) {
                victoriaApp.appRepository.loadIcon(app)
            }
        }