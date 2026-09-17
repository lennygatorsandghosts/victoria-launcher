// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind

fun recentlyInstalled(rows: List<AppInfo>, hidden: Set<String>, max: Int = 5): List<AppInfo> =
    recentlyInstalledRows(
        rows = rows,
        hidden = hidden,
        max = max,
        kind = { it.kind },
        firstInstallTime = { it.firstInstallTime },
        key = { it.key },
        label = { it.label },
    )

internal fun <T> recentlyInstalledRows(
    rows: List<T>,
    hidden: Set<String>,
    max: Int = 5,
    kind: (T) -> EntryKind,
    firstInstallTime: (T) -> Long,
    key: (T) -> String,
    label: (T) -> String,
): List<T> {
    if (max <= 0) return emptyList()
    return rows
        .asSequence()
        .filter { kind(it) == EntryKind.APP && firstInstallTime(it) > 0L && key(it) !in hidden }
        .sortedWith(
            compareByDescending<T> { firstInstallTime(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { label(it) }
                .thenBy { label(it) }
        )
        .take(max)
        .toList()
}
