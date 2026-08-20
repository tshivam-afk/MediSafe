package com.medisafe

import android.app.Application
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.NotificationHelper

class MediSafeApp : Application() {
    @Volatile
    var pendingColdStartUpdateCheck: Boolean = true

    override fun onCreate() {
        super.onCreate()
        pendingColdStartUpdateCheck = true
        NotificationHelper.ensureChannel(this)
        AlarmScheduler.cancelWidgetRefresh(this)
    }
}
