// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.os.UserHandle

data class ShortcutCandidate(
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val user: UserHandle,
    val isPinned: Boolean,
    val key: String,
    val appLabel: String = packageName,
)

data class ShortcutCandidateGroup<T>(
    val appLabel: String,
    val shortcuts: List<T>,
)

fun <T> groupShortcutCandidates(
    candidates: List<T>,
    appLabel: (T) -> String,
    shortcutLabel: (T) -> String,
): List<ShortcutCandidateGroup<T>> =
    candidates
        .sortedWith(
            compareBy<T> { appLabel(it).lowercase() }
                .thenBy { appLabel(it) }
                .thenBy { shortcutLabel(it).lowercase() }
                .thenBy { shortcutLabel(it) },
        )
        .groupBy(appLabel)
        .map { (label, shortcuts) -> ShortcutCandidateGroup(label, shortcuts) }
