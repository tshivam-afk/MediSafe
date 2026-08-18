package com.medisafe

import android.app.Application
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.repository.ReminderRepository
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.NotificationHelper
import com.medisafe.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediSafeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        AlarmScheduler.rescheduleAllActive(this)
        ReminderAppWidgetProvider.updateAllWidgets(this)
        appScope.launch {
            runCatching {
                ReminderRepository(
                    AppDatabase.getInstance(this@MediSafeApp).reminderDao(),
                    this@MediSafeApp
                ).scanAndLogMissed()
            }
        }
    }
}
