package com.medisafe.notifications

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.repository.ReminderRepository
import com.medisafe.ui.theme.MediSafeTheme
import com.medisafe.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AlarmRingActivity : FragmentActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_RINGING) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reminderId = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        val filter = IntentFilter(ACTION_STOP_RINGING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        setContent {
            MediSafeTheme {
                var item by remember { mutableStateOf<ReminderItem?>(null) }
                LaunchedEffect(reminderId) {
                    if (reminderId == -1L) return@LaunchedEffect
                    item = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(this@AlarmRingActivity).reminderDao().getReminderById(reminderId)
                    }
                    val reminder = item
                    // AlarmService normally already rings before this screen appears. Only
                    // start audio here if it somehow isn't playing (e.g. the service was
                    // refused), so we never double-play the tone.
                    if (reminder != null && !AlarmPlayer.isPlaying) {
                        AlarmService.start(this@AlarmRingActivity, reminder.id)
                    }
                }
                AlarmRingScreen(
                    item = item,
                    onTake = { act { repo().markDoneOrTaken(it) } },
                    onSnooze = { act { repo().snoozeReminder(it, 15) } },
                    onSkip = { act { repo().skipReminder(it) } },
                    onDismiss = { stopAndFinish() }
                )
            }
        }
    }

    private fun act(block: suspend (ReminderItem) -> Unit) {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        Thread {
            runCatching {
                val reminder = repo().getReminderById(id) ?: return@runCatching
                runBlocking { block(reminder) }
                ReminderAppWidgetProvider.updateAllWidgets(this)
            }
            runOnUiThread { stopAndFinish() }
        }.start()
    }

    private fun repo() = ReminderRepository(
        AppDatabase.getInstance(this).reminderDao(),
        applicationContext
    )

    private fun stopAndFinish() {
        AlarmService.stop(this)
        AlarmPlayer.stop(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (id != -1L) {
            nm.cancel(AlarmScheduler.requestCode(id))
            AlarmScheduler.cancelEscalations(this, id)
        }
        nm.cancel(AlarmService.NOTIFICATION_ID)
        finish()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_RINGING = "com.medisafe.app.STOP_ALARM_RINGING"
    }
}

@Composable
private fun AlarmRingScreen(
    item: ReminderItem?,
    onTake: (ReminderItem) -> Unit,
    onSnooze: (ReminderItem) -> Unit,
    onSkip: (ReminderItem) -> Unit,
    onDismiss: () -> Unit
) {
    val reminder = item
    val isMed = reminder?.categoryEnum == ReminderCategory.MEDICATION
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Alarm,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ALARM",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            reminder?.title ?: "Reminder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        val subtitle = reminder?.doseLabel.orEmpty().ifBlank { reminder?.foodTimingEnum?.displayName.orEmpty() }
        if (subtitle.isNotBlank() && subtitle != "No food rule") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (reminder != null) {
            Button(
                onClick = { onTake(reminder) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isMed) "Take now" else "Mark done", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onSnooze(reminder) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) { Text("Snooze 15 min") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSkip(reminder) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) { Text("Skip this time") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Stop ringing") }
    }
}
