// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R

@Composable
fun ConfirmDeleteBookmarkDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_bookmark_title)) },
        text = { Text(stringResource(R.string.delete_bookmark_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_bookmark_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
