package com.example

import android.app.Application
import com.example.notifications.AlarmScheduler
import com.example.notifications.NotificationHelper
import com.example.widget.ReminderAppWidgetProvider

class MediSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        AlarmScheduler.rescheduleAllActive(this)
        ReminderAppWidgetProvider.updateAllWidgets(this)
    }
}
