package com.medisafe.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medisafe.data.model.ReminderCategory
import com.medisafe.ui.viewmodel.ConfirmRequest

@Composable
fun ConfirmRequestDialog(
    request: ConfirmRequest,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("10") }
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
        is ConfirmRequest.BatchTake -> Triple(
            "Take ${request.items.size} doses?",
            request.items.joinToString { it.title },
            "Take all"
        )
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
        is ConfirmRequest.Refill -> Triple(
            "Log a refill?",
            "How many pills did you add for “${request.item.title}”?",
            "Add pills"
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
        text = {
            Column {
                Text(body)
                if (request is ConfirmRequest.Take || request is ConfirmRequest.BatchTake) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(80) },
                        label = { Text("Note (optional)") },
                        placeholder = { Text("with food, felt dizzy…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (request is ConfirmRequest.Refill) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                        label = { Text("Pills added") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val extra = when (request) {
                        is ConfirmRequest.Refill -> amount
                        is ConfirmRequest.Take, is ConfirmRequest.BatchTake -> note
                        else -> ""
                    }
                    onConfirm(extra)
                }
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
