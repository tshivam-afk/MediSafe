package com.medisafe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisafe.data.model.Priority
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.util.DateTimeUtils
import com.medisafe.util.DoseTimes
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditReminderSheet(
    initialItem: ReminderItem?,
    onDismiss: () -> Unit,
    onSave: (ReminderItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (initialItem == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf(initialItem.categoryEnum) }
    var title by remember { mutableStateOf(initialItem.title) }
    var dosageOrDetails by remember { mutableStateOf(initialItem.dosageOrDetails) }
    var scheduledTimeMillis by remember { mutableLongStateOf(initialItem.scheduledTimeMillis) }
    var recurrence by remember { mutableStateOf(initialItem.recurrenceEnum) }
    var priority by remember { mutableStateOf(initialItem.priorityEnum) }
    var soundEnabled by remember { mutableStateOf(initialItem.notificationSound) }
    var vibrateEnabled by remember { mutableStateOf(initialItem.vibrate) }
    var titleError by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(initialItem.customIntervalHours.coerceAtLeast(1)) }
    var doseTimes by remember { mutableStateOf(initialItem.parsedDoseTimes.ifEmpty {
        val cal = Calendar.getInstance().apply { timeInMillis = initialItem.scheduledTimeMillis }
        listOf(cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE))
    }) }
    var pillsText by remember { mutableStateOf(initialItem.pillsRemaining?.toString().orEmpty()) }
    var thresholdText by remember { mutableStateOf(initialItem.refillThreshold.toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialItem.id == 0L) "New Reminder" else "Edit Reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("CATEGORY")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = category
                            if (category == ReminderCategory.EVENT && recurrence == RecurrenceType.DAILY) {
                                recurrence = RecurrenceType.ONCE
                            }
                        },
                        label = { Text("${category.emoji} ${category.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text(if (selectedCategory == ReminderCategory.MEDICATION) "Medication / Pill Name *" else "Title *") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Title is required", color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_title_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = dosageOrDetails,
                onValueChange = { dosageOrDetails = it },
                label = { Text(if (selectedCategory == ReminderCategory.MEDICATION) "Dosage & Instructions" else "Notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_details_input"),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("DATE")
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("date_picker_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(DateTimeUtils.formatDate(scheduledTimeMillis), maxLines = 1)
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(if (isInterval(recurrence)) "START TIME" else "DOSE TIMES")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                doseTimes.forEachIndexed { index, pair ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            timePickerTarget = index
                            showTimePicker = true
                        },
                        label = { Text(DoseTimes.formatDisplay(pair.first, pair.second)) },
                        trailingIcon = {
                            if (doseTimes.size > 1) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove time",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            doseTimes = doseTimes.filterIndexed { i, _ -> i != index }
                                        }
                                )
                            }
                        }
                    )
                }
                if (!isInterval(recurrence)) {
                    AssistAddChip("Add time") {
                        timePickerTarget = -1
                        showTimePicker = true
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("RECURRENCE")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecurrenceType.entries.forEach { rec ->
                    FilterChip(
                        selected = recurrence == rec,
                        onClick = { recurrence = rec },
                        label = { Text(rec.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (recurrence == RecurrenceType.CUSTOM_HOURS) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customHours.toString(),
                    onValueChange = { customHours = it.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 72) ?: 1 },
                    label = { Text("Every N hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedCategory == ReminderCategory.MEDICATION) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("REFILL TRACKING")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pillsText,
                        onValueChange = { pillsText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Pills left") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Remind at") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("PRIORITY")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { prio ->
                    val isSelected = priority == prio
                    val prioColor = when (prio) {
                        Priority.LOW -> Color(0xFF5B6B79)
                        Priority.NORMAL -> Color(0xFF6750A4)
                        Priority.HIGH -> Color(0xFFE65100)
                        Priority.URGENT -> Color(0xFFBA1A1A)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) prioColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) BorderStroke(1.dp, prioColor) else null,
                        modifier = Modifier.clickable { priority = prio }
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                prio.displayName,
                                color = if (isSelected) prioColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingSwitch("Notification sound", Icons.Outlined.Notifications, soundEnabled) { soundEnabled = it }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitch("Vibrate on alert", Icons.Outlined.Vibration, vibrateEnabled) { vibrateEnabled = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val first = doseTimes.firstOrNull() ?: (8 to 0)
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = scheduledTimeMillis
                        set(Calendar.HOUR_OF_DAY, first.first)
                        set(Calendar.MINUTE, first.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSave(
                        initialItem.copy(
                            title = title.trim(),
                            category = selectedCategory.name,
                            dosageOrDetails = dosageOrDetails.trim(),
                            scheduledTimeMillis = cal.timeInMillis,
                            recurrence = recurrence.name,
                            customIntervalHours = customHours,
                            priority = priority.name,
                            notificationSound = soundEnabled,
                            vibrate = vibrateEnabled,
                            doseTimes = DoseTimes.format(doseTimes),
                            pillsRemaining = pillsText.toIntOrNull(),
                            refillThreshold = thresholdText.toIntOrNull() ?: 5
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_reminder_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (initialItem.id == 0L) "Save Reminder & Alert" else "Update Reminder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = scheduledTimeMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        val old = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
                        val picked = Calendar.getInstance().apply { timeInMillis = selected }
                        scheduledTimeMillis = DateTimeUtils.createTimestamp(
                            picked.get(Calendar.YEAR),
                            picked.get(Calendar.MONTH),
                            picked.get(Calendar.DAY_OF_MONTH),
                            old.get(Calendar.HOUR_OF_DAY),
                            old.get(Calendar.MINUTE)
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val existing = timePickerTarget?.let { doseTimes.getOrNull(it) } ?: (8 to 0)
        val timeState = rememberTimePickerState(initialHour = existing.first, initialMinute = existing.second, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val next = timeState.hour to timeState.minute
                    doseTimes = when (val target = timePickerTarget) {
                        null, -1 -> (doseTimes + next).distinct()
                        else -> doseTimes.mapIndexed { i, pair -> if (i == target) next else pair }
                    }.sortedWith(compareBy({ it.first }, { it.second }))
                    val first = doseTimes.first()
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = scheduledTimeMillis
                        set(Calendar.HOUR_OF_DAY, first.first)
                        set(Calendar.MINUTE, first.second)
                    }
                    scheduledTimeMillis = cal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun AssistAddChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isInterval(type: RecurrenceType): Boolean {
    return type == RecurrenceType.EVERY_4_HOURS ||
        type == RecurrenceType.EVERY_6_HOURS ||
        type == RecurrenceType.EVERY_8_HOURS ||
        type == RecurrenceType.EVERY_12_HOURS ||
        type == RecurrenceType.CUSTOM_HOURS
}
