package com.medisafe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisafe.data.model.DayAdherence
import com.medisafe.util.DateTimeUtils
import java.util.Calendar

@Composable
fun AdherenceChart(
    days: List<DayAdherence>,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val takenColor = Color(0xFF10B981)
    val missedColor = Color(0xFFE11D48)
    val skippedColor = Color(0xFFF59E0B)
    val weekTaken = days.sumOf { it.taken }
    val weekTotal = days.sumOf { it.total }
    val weekPct = if (weekTotal == 0) 100 else ((weekTaken.toFloat() / weekTotal) * 100).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "LAST 7 DAYS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                text = if (weekTotal == 0) "No activity this week" else "$weekPct% taken · $weekTaken of $weekTotal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val barWidth = size.width / (days.size * 2f)
                val maxValue = days.maxOf { it.total }.coerceAtLeast(1)
                days.forEachIndexed { index, day ->
                    val x = (index + 0.5f) * (size.width / days.size) - barWidth / 2f
                    val totalHeight = (day.total / maxValue.toFloat()) * size.height
                    var y = size.height
                    fun stack(count: Int, color: Color) {
                        if (count <= 0) return
                        val h = (count / maxValue.toFloat()) * size.height
                        y -= h
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                    stack(day.skipped, skippedColor)
                    stack(day.missed, missedColor)
                    stack(day.taken, takenColor)
                    if (day.total == 0) {
                        drawRoundRect(
                            color = Color.LightGray.copy(alpha = 0.35f),
                            topLeft = Offset(x, size.height - 8f),
                            size = Size(barWidth, 8f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    val label = Calendar.getInstance().apply { timeInMillis = day.dayStartMillis }
                        .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault())
                        ?: DateTimeUtils.formatShortDate(day.dayStartMillis)
                    Text(
                        text = label.take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val Int.spSafe get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
