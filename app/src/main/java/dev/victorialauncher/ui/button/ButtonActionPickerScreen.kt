// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ButtonAction
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.ui.applist.AppListModel
import dev.victorialauncher.ui.applist.AppListRow
import dev.victorialauncher.ui.common.AppIcon

@Composable
fun ButtonActionPickerScreen(
    title: String,
    current: ButtonAction,
    model: AppListModel,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    hasPrivateSpace: Boolean,
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
                SectionHeader("Current")
                Text(
                    actionLabel(current) { key -> model.rows.asSequence()
                        .filterIsInstance<AppListRow.Entry>()
                        .firstOrNull { it.app.key == key }
                        ?.app
                        ?.let(::displayName)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }

            item { SectionHeader("Actions") }
            val builtIns = buildList {
                add(ButtonAction.None)
                add(ButtonAction.WebSearch)
                add(ButtonAction.SearchApps)
                add(ButtonAction.Notifications)
                add(ButtonAction.LockScreen)
                add(ButtonAction.LauncherSettings)
                if (hasPrivateSpace) add(ButtonAction.TogglePrivateSpace)
            }
            builtIns.forEach { action ->
                item(key = action.encode()) {
                    ActionRow(
                        label = actionLabel(action) { null },
                        selected = current == action,
                        onClick = { onPick(action) },
                    )
                }
            }

            // TODO(app-shortcuts): a later task supplies listable shortcut rows here.
            item { appShortcutsSection() }

            model.rows.forEach { row ->
                when (row) {
                    is AppListRow.Header -> item(key = "header:${row.text}") { SectionHeader(row.text) }
                    is AppListRow.Entry -> {
                        val offered = when (row.app.kind) {
                            EntryKind.APP, EntryKind.SHORTCUT, EntryKind.SEARCH -> true
                            EntryKind.PRIVATE_SPACE -> false
                        }
                        if (offered) {
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
