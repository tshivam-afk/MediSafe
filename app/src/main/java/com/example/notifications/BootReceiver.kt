package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.widget.ReminderAppWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val shouldReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        if (!shouldReschedule) return

        Log.d("BootReceiver", "Rescheduling alarms after $action")
        val pendingResult = goAsync()
        try {
            NotificationHelper.ensureChannel(context)
            AlarmScheduler.rescheduleAllActive(context)
            ReminderAppWidgetProvider.updateAllWidgets(context)
        } finally {
            pendingResult.finish()
        }
    }
}
