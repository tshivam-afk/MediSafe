package com.medisafe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisafe.data.model.LogAction
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderLog
import com.medisafe.ui.viewmodel.HistoryFilters
import com.medisafe.util.DateTimeUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryLogTab(
    logs: List<ReminderLog>,
    filters: HistoryFilters,
    onFiltersChange: (HistoryFilters) -> Unit,
    onClearAll: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onUndoLog: (ReminderLog) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dose & Task History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${logs.size} matching activities", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (logs.isNotEmpty()) {
                OutlinedButton(onClick = onClearAll, modifier = Modifier.testTag("clear_history_button")) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = filters.query,
            onValueChange = { onFiltersChange(filters.copy(query = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search history") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filters.category == null && filters.action == null && filters.fromMillis == null,
                onClick = { onFiltersChange(HistoryFilters(query = filters.query)) },
                label = { Text("All") }
            )
            ReminderCategory.entries.forEach { category ->
                FilterChip(
                    selected = filters.category == category,
                    onClick = {
                        onFiltersChange(filters.copy(category = category.takeUnless { filters.category == category }))
                    },
                    label = { Text(category.emoji + " " + category.displayName) }
                )
            }
            listOf(LogAction.TAKEN, LogAction.MISSED, LogAction.SKIPPED, LogAction.SNOOZED, LogAction.REFILLED).forEach { action ->
                FilterChip(
                    selected = filters.action == action,
                    onClick = {
                        onFiltersChange(filters.copy(action = action.takeUnless { filters.action == action }))
                    },
                    label = { Text(action.displayName) }
                )
            }
            FilterChip(
                selected = filters.fromMillis != null,
                onClick = {
                    val start = if (filters.fromMillis == null) DateTimeUtils.startOfDay() else null
                    onFiltersChange(filters.copy(fromMillis = start, toMillis = if (start == null) null else start + 24L * 60 * 60 * 1000 - 1))
                },
                label = { Text("Today") }
            )
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No matching history", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Completed, missed, and snoozed events show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val (actionColor, actionIcon, actionLabel) = when (log.actionEnum) {
                        LogAction.TAKEN, LogAction.COMPLETED -> Triple(Color(0xFF10B981), Icons.Default.CheckCircle, log.actionEnum.displayName)
                        LogAction.SNOOZED -> Triple(Color(0xFFF59E0B), Icons.Default.Snooze, "Snoozed")
                        LogAction.SKIPPED -> Triple(Color(0xFFEF4444), Icons.Default.Close, "Skipped")
                        LogAction.MISSED -> Triple(Color(0xFFB45309), Icons.Default.Warning, "Missed")
                        LogAction.REFILLED -> Triple(Color(0xFF0284C7), Icons.Default.CheckCircle, "Refilled")
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("log_item_${log.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(actionColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(actionIcon, contentDescription = actionLabel, tint = actionColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.reminderTitle, fontWeight = FontWeight.Bold, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = actionColor.copy(alpha = 0.15f)) {
                                        Text(actionLabel, color = actionColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Text(DateTimeUtils.formatDateTime(log.timestampMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (log.actionEnum == LogAction.TAKEN || log.actionEnum == LogAction.COMPLETED) {
                                IconButton(onClick = { onUndoLog(log) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.Undo, contentDescription = "Undo log", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { onDeleteLog(log.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Delete log", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
