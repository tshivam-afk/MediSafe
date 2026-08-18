package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Priority
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderItem
import com.example.util.DateTimeUtils
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
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf(initialItem.categoryEnum) }
    var title by remember { mutableStateOf(initialItem.title) }
    var dosageOrDetails by remember { mutableStateOf(initialItem.dosageOrDetails) }
    var scheduledTimeMillis by remember { mutableLongStateOf(initialItem.scheduledTimeMillis) }
    var recurrence by remember { mutableStateOf(initialItem.recurrenceEnum) }
    var priority by remember { mutableStateOf(initialItem.priorityEnum) }
    var soundEnabled by remember { mutableStateOf(initialItem.notificationSound) }
    var vibrateEnabled by remember { mutableStateOf(initialItem.vibrate) }
    var titleError by remember { mutableStateOf(false) }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialItem.id == 0L) "New Reminder" else "Edit Reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selection Chips
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

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
                        label = {
                            Text(
                                text = "${category.emoji} ${category.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Presets
            Text(
                text = "QUICK TEMPLATES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (selectedCategory) {
                    ReminderCategory.MEDICATION -> {
                        PresetChip("Morning Med (8 AM)") {
                            title = "Morning Medication"
                            dosageOrDetails = "1 dose with breakfast"
                            val c = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 8)
                                set(Calendar.MINUTE, 0)
                                if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            scheduledTimeMillis = c.timeInMillis
                            recurrence = RecurrenceType.DAILY
                        }
                        PresetChip("Evening Med (8 PM)") {
                            title = "Evening Dose"
                            dosageOrDetails = "1 dose after dinner"
                            val c = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 20)
                                set(Calendar.MINUTE, 0)
                                if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            scheduledTimeMillis = c.timeInMillis
                            recurrence = RecurrenceType.DAILY
                        }
                        PresetChip("Antibiotics (q8h)") {
                            title = "Amoxicillin 500mg"
                            dosageOrDetails = "Take every 8 hours with full glass of water"
                            recurrence = RecurrenceType.EVERY_8_HOURS
                        }
                    }
                    ReminderCategory.DAILY_TASK -> {
                        PresetChip("Hydration (500ml)") {
                            title = "Drink Water (500ml)"
                            dosageOrDetails = "Stay hydrated throughout the day"
                            recurrence = RecurrenceType.EVERY_4_HOURS
                        }
                        PresetChip("Posture & Stretch") {
                            title = "Stretch & Stand"
                            dosageOrDetails = "5-minute posture reset"
                            recurrence = RecurrenceType.DAILY
                        }
                        PresetChip("Team Standup") {
                            title = "Daily Standup Meeting"
                            dosageOrDetails = "Discuss daily blockers and priorities"
                            recurrence = RecurrenceType.WEEKDAYS
                        }
                    }
                    ReminderCategory.EVENT -> {
                        PresetChip("Doctor Visit") {
                            title = "Doctor Appointment"
                            dosageOrDetails = "Clinic visit - checkup and prescription renewal"
                            recurrence = RecurrenceType.ONCE
                        }
                        PresetChip("Bill Payment") {
                            title = "Pay Monthly Bills"
                            dosageOrDetails = "Electricity, Internet & Rent"
                            recurrence = RecurrenceType.ONCE
                        }
                    }
                    ReminderCategory.OTHER -> {
                        PresetChip("General Reminder") {
                            title = "Important Reminder"
                            recurrence = RecurrenceType.ONCE
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = {
                    Text(
                        when (selectedCategory) {
                            ReminderCategory.MEDICATION -> "Medication / Pill Name *"
                            ReminderCategory.DAILY_TASK -> "Task Title *"
                            ReminderCategory.EVENT -> "Event Name *"
                            else -> "Reminder Title *"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when (selectedCategory) {
                            ReminderCategory.MEDICATION -> "e.g., Metformin 500mg"
                            ReminderCategory.DAILY_TASK -> "e.g., Drink 500ml water"
                            ReminderCategory.EVENT -> "e.g., Annual Dentist Visit"
                            else -> "e.g., Important follow-up"
                        }
                    )
                },
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

            // Details / Dosage Field
            OutlinedTextField(
                value = dosageOrDetails,
                onValueChange = { dosageOrDetails = it },
                label = {
                    Text(
                        when (selectedCategory) {
                            ReminderCategory.MEDICATION -> "Dosage & Instructions"
                            ReminderCategory.DAILY_TASK -> "Task Details / Notes"
                            ReminderCategory.EVENT -> "Location & Notes"
                            else -> "Notes"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when (selectedCategory) {
                            ReminderCategory.MEDICATION -> "e.g., 2 capsules with food"
                            ReminderCategory.DAILY_TASK -> "e.g., 3 sets of 15 reps"
                            ReminderCategory.EVENT -> "e.g., City Medical Center, Room 302"
                            else -> "e.g., Additional details"
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_details_input"),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time Picker Section
            Text(
                text = "DATE & TIME",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Button
                OutlinedButton(
                    onClick = {
                        val currentCal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = scheduledTimeMillis
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                scheduledTimeMillis = newCal.timeInMillis
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("date_picker_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatShortDate(scheduledTimeMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                // Time Button
                OutlinedButton(
                    onClick = {
                        val currentCal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = scheduledTimeMillis
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                }
                                scheduledTimeMillis = newCal.timeInMillis
                            },
                            currentCal.get(Calendar.HOUR_OF_DAY),
                            currentCal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("time_picker_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatTime(scheduledTimeMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recurrence Picker
            Text(
                text = "RECURRENCE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RecurrenceType.entries.forEach { rec ->
                    val isSelected = recurrence == rec
                    FilterChip(
                        selected = isSelected,
                        onClick = { recurrence = rec },
                        label = {
                            Text(
                                text = rec.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Selector
            Text(
                text = "PRIORITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
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
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = prio.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) prioColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alert Settings: Sound & Vibrate
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Notification Alert Sound",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Vibrate on Alert",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2
                            )
                        }
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = { vibrateEnabled = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save / Cancel Buttons
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val itemToSave = initialItem.copy(
                        title = title.trim(),
                        category = selectedCategory.name,
                        dosageOrDetails = dosageOrDetails.trim(),
                        scheduledTimeMillis = scheduledTimeMillis,
                        recurrence = recurrence.name,
                        priority = priority.name,
                        notificationSound = soundEnabled,
                        vibrate = vibrateEnabled
                    )
                    onSave(itemToSave)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_reminder_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialItem.id == 0L) "Save Reminder & Alert" else "Update Reminder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
