package com.medisafe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.util.DateTimeUtils

@Composable
fun CompactNextUp(
    reminder: ReminderItem?,
    currentTimeMillis: Long,
    onTakeOrDone: (ReminderItem) -> Unit,
    onClick: (ReminderItem) -> Unit,
    batchCount: Int = 1,
    onTakeSlot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (reminder == null) return
    val countdown = DateTimeUtils.getCountdown(reminder.effectiveTriggerTimeMillis, currentTimeMillis)
    val isDue = countdown.isDue
    val action = if (reminder.categoryEnum == ReminderCategory.MEDICATION) "Take" else "Done"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick(reminder) }
            .testTag("hero_countdown_card"),
        shape = RoundedCornerShape(16.dp),
        color = if (isDue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDue) "Due now" else "Next up",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${reminder.categoryEnum.emoji} ${reminder.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isDue) "NOW" else countdown.formattedString,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            if (batchCount > 1) {
                TextButton(onClick = onTakeSlot) {
                    Text("$action all $batchCount", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { onTakeOrDone(reminder) }) {
                    Text(action, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
