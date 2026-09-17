// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.victorialauncher.data.ButtonAction
import dev.victorialauncher.data.ButtonSlot

@Composable
fun ButtonEditSheet(
    actions: Map<ButtonSlot, ButtonAction>,
    resolveEntryLabel: (String) -> String?,
    onEdit: (ButtonSlot) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Vicky+ button",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            SheetRow(
                label = "Edit tap action",
                value = actionLabel(actions[ButtonSlot.TAP] ?: ButtonAction.None, resolveEntryLabel),
                onClick = { onEdit(ButtonSlot.TAP) },
            )
            SheetRow(
                label = "Edit swipe up action",
                value = actionLabel(actions[ButtonSlot.SWIPE_UP] ?: ButtonAction.None, resolveEntryLabel),
                onClick = { onEdit(ButtonSlot.SWIPE_UP) },
            )
            SheetRow(
                label = "Edit swipe left action",
                value = actionLabel(actions[ButtonSlot.SWIPE_LEFT] ?: ButtonAction.None, resolveEntryLabel),
                onClick = { onEdit(ButtonSlot.SWIPE_LEFT) },
            )
            SheetRow(
                label = "Edit swipe right action",
                value = actionLabel(actions[ButtonSlot.SWIPE_RIGHT] ?: ButtonAction.None, resolveEntryLabel),
                onClick = { onEdit(ButtonSlot.SWIPE_RIGHT) },
            )
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text(
                    "Vicky+ settings",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}
