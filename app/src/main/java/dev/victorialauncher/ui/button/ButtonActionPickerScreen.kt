// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ButtonAction
import dev.victorialauncher.data.ShortcutCandidate
import dev.victorialauncher.data.groupShortcutCandidates
import dev.victorialauncher.ui.applist.AppListModel
import dev.victorialauncher.ui.applist.AppListRow
import dev.victorialauncher.ui.common.AppIcon

@Composable
fun ButtonActionPickerScreen(
    title: String,
    current: ButtonAction,
    model: AppListModel,
    currentLabel: (String) -> String?,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onPick: (ButtonAction) -> Unit,
    onBack: () -> Unit,
    appShortcutsSection: @Composable () -> Unit = {},
) {
    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SectionHeader(stringResource(R.string.button_picker_current))
                Text(
                    actionLabel(current, currentLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.button_picker_actions)) }
            val builtIns = listOf(
                ButtonAction.None,
                ButtonAction.WebSearch,
                ButtonAction.SearchApps,
                ButtonAction.Notifications,
                ButtonAction.LockScreen,
                ButtonAction.LauncherSettings,
            )
            builtIns.forEach { action ->
                item(key = action.encode()) {
                    ActionRow(
                        label = actionLabel(action) { null },
                        selected = current == action,
                        onClick = { onPick(action) },
                    )
                }
            }

            item { appShortcutsSection() }

            model.rows.forEach { row ->
                when (row) {
                    is AppListRow.Header -> item(key = "header:${row.text}") { SectionHeader(row.text) }
                    is AppListRow.Entry -> {
                        item(key = row.app.key) {
                            AppActionRow(
                                app = row.app,
                                label = displayName(row.app),
                                iconSizeDp = iconSizeDp,
                                selected = current == ButtonAction.LaunchEntry(row.app.key),
                                onClick = { onPick(ButtonAction.LaunchEntry(row.app.key)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppShortcutsSection(
    candidates: List<ShortcutCandidate>,
    current: ButtonAction,
    onPick: (ShortcutCandidate) -> Unit,
) {
    if (candidates.isEmpty()) return

    val groups = remember(candidates) {
        groupShortcutCandidates(candidates, ShortcutCandidate::appLabel, ShortcutCandidate::label)
    }
    val selectedKey = (current as? ButtonAction.LaunchEntry)?.key
    var expanded by remember(candidates, selectedKey) {
        mutableStateOf(
            groups
                .filter { group -> group.shortcuts.any { it.key == selectedKey } }
                .map { it.appLabel }
                .toSet(),
        )
    }

    SectionHeader(stringResource(R.string.button_picker_app_shortcuts))
    groups.forEach { group ->
        val isExpanded = group.appLabel in expanded
        ShortcutAppRow(
            label = group.appLabel,
            expanded = isExpanded,
            onClick = {
                expanded = if (isExpanded) expanded - group.appLabel else expanded + group.appLabel
            },
        )
        if (isExpanded) {
            group.shortcuts.forEach { shortcut ->
                ShortcutCandidateRow(
                    shortcut = shortcut,
                    selected = selectedKey == shortcut.key,
                    onClick = { onPick(shortcut) },
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ShortcutAppRow(label: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutCandidateRow(
    shortcut: ShortcutCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shortcut.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ActionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun AppActionRow(
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onClick)
    }
}
