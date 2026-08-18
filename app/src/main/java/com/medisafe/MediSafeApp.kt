package com.medisafe

import android.app.Application
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.NotificationHelper

class MediSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        AlarmScheduler.cancelWidgetRefresh(this)
    }
}
