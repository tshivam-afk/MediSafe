package com.medisafe.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.medisafe.data.model.ReminderCategory
import com.medisafe.ui.viewmodel.ConfirmRequest

@Composable
fun ConfirmRequestDialog(
    request: ConfirmRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, body, confirm) = when (request) {
        is ConfirmRequest.Delete -> {
            val count = request.items.size
            Triple(
                if (count == 1) "Delete reminder?" else "Delete $count reminders?",
                if (count == 1) "“${request.items.first().title}” will be removed. You can undo from the snackbar."
                else "Selected reminders will be removed. You can undo from the snackbar.",
                "Delete"
            )
        }
        is ConfirmRequest.Take -> {
            val isMed = request.item.categoryEnum == ReminderCategory.MEDICATION
            Triple(
                if (isMed) "Mark as taken?" else "Mark as done?",
                "Log “${request.item.title}” now? This writes a history entry.",
                if (isMed) "Take now" else "Mark done"
            )
        }
        is ConfirmRequest.Skip -> Triple(
            "Skip this occurrence?",
            "“${request.item.title}” will be skipped and logged. You can undo from the snackbar.",
            "Skip"
        )
        is ConfirmRequest.UndoLog -> Triple(
            "Undo this history entry?",
            "Remove “${request.log.reminderTitle} · ${request.log.actionEnum.displayName}” from history.",
            "Undo"
        )
        is ConfirmRequest.ClearHistory -> Triple(
            "Clear all history?",
            "Every dose and task log will be permanently removed. This cannot be undone.",
            "Clear"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
