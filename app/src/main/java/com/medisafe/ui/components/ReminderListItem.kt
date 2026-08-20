package com.medisafe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisafe.data.model.Priority
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.util.DateTimeUtils

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReminderListItem(
    item: ReminderItem,
    currentTimeMillis: Long,
    onTakeOrDone: (ReminderItem) -> Unit,
    onEdit: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit,
    onSnooze: (ReminderItem) -> Unit,
    onDuplicate: (ReminderItem) -> Unit = {},
    onRefill: (ReminderItem) -> Unit = {},
    onClick: (ReminderItem) -> Unit,
    onLongClick: (ReminderItem) -> Unit = {},
    selected: Boolean = false,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val countdown = DateTimeUtils.getCountdown(item.effectiveTriggerTimeMillis, currentTimeMillis)
    val isDue = countdown.isDue && !item.isCompleted

    val categoryColor = when (item.categoryEnum) {
        ReminderCategory.MEDICATION -> Color(0xFF6750A4)
        ReminderCategory.DAILY_TASK -> Color(0xFF0284C7)
        ReminderCategory.EVENT -> Color(0xFFD97706)
        else -> Color(0xFF9333EA)
    }

    val cardBgColor by animateColorAsState(
        targetValue = when {
            item.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isDue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "card_bg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick(item) }
            .testTag("reminder_item_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark / Complete Toggle Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isCompleted) Color(0xFF10B981)
                        else categoryColor.copy(alpha = 0.12f)
                    )
                    .clickable {
                        if (selectionMode) onLongClick(item) else onTakeOrDone(item)
                    }
                    .testTag("checkbox_toggle_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (selected || item.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = item.categoryEnum.emoji,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Shown for the explicit switch and for high/urgent, which always ring.
                    if (item.ringsAsAlarm) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEE2E2)) {
                            Text(
                                text = "ALARM",
                                color = Color(0xFFB91C1C),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.isPrn) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE0E7FF)) {
                            Text(
                                text = "PRN",
                                color = Color(0xFF3730A3),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.isExpired || item.expirySoon) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEE2E2)) {
                            Text(
                                text = if (item.isExpired) "EXPIRED" else "EXPIRING",
                                color = Color(0xFFB91C1C),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.needsRefill) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFEDD5)) {
                            Text(
                                text = "REFILL ${item.pillsRemaining}",
                                color = Color(0xFFC2410C),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.priorityEnum == Priority.HIGH || item.priorityEnum == Priority.URGENT) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.priorityEnum == Priority.URGENT) Color(0xFFFEE2E2) else Color(0xFFFFEDD5)
                        ) {
                            Text(
                                text = item.priorityEnum.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.priorityEnum == Priority.URGENT) Color(0xFFDC2626) else Color(0xFFC2410C),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                    val subtitle = item.doseLabel
                    if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(subtitle)
                            if (item.foodTimingEnum != com.medisafe.data.model.FoodTiming.NONE) {
                                append(" · ")
                                append(item.foodTimingEnum.displayName)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = DateTimeUtils.getRelativeTimeLabel(item.effectiveTriggerTimeMillis),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isDue) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.recurrenceEnum != RecurrenceType.ONCE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = item.recurrenceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Live Countdown Chip
                    if (!item.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDue) Color(0xFFFFE4E6) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = if (isDue) "Due Now" else countdown.formattedString,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDue) Color(0xFFBE123C) else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (!selectionMode) Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("menu_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!item.isCompleted) {
                        DropdownMenuItem(
                            text = { Text("Snooze (+15m)") },
                            onClick = {
                                menuExpanded = false
                                onSnooze(item)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Snooze, contentDescription = null)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit(item)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete(item)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}
