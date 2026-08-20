package com.medisafe.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED
        ) return

        Log.d("TimeChangeReceiver", "Rebuilding schedules after $action")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderRepository(
                    AppDatabase.getInstance(context).reminderDao(),
                    context.applicationContext
                ).rebuildSchedulesAfterTimeChange()
                AlarmScheduler.scheduleDailyRecap(context)
            } catch (e: Exception) {
                Log.e("TimeChangeReceiver", "Failed to rebuild schedules", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
