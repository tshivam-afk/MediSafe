package com.medisafe

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medisafe.data.model.ReminderCategory
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.ReminderAlarmReceiver
import com.medisafe.ui.screens.LockScreen
import com.medisafe.ui.screens.MainScreen
import com.medisafe.ui.screens.OnboardingScreen
import com.medisafe.ui.theme.MediSafeTheme
import com.medisafe.ui.viewmodel.ReminderViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlarmScheduler.rescheduleAllActive(this)
        // Route extras only on a true cold start. On recreation (rotation, dark-mode
        // toggle…) the original intent is re-delivered and would otherwise re-open the
        // detail sheet the user already dismissed.
        if (savedInstanceState == null) handleIncomingIntent(intent)

        setContent {
            val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
            val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
            MediSafeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        showOnboarding -> OnboardingScreen(
                            onFinished = {
                                viewModel.completeOnboarding()
                                viewModel.openCreateSheet(ReminderCategory.MEDICATION)
                            }
                        )
                        isLocked -> LockScreen(
                            biometricEnabled = viewModel.preferences.biometricEnabled,
                            onUnlockWithPin = { viewModel.verifyPin(it) },
                            onBiometricUnlock = { viewModel.unlock() }
                        )
                        else -> MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.lockIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            "com.medisafe.app.ADD_REMINDER" -> viewModel.openCreateSheet(ReminderCategory.MEDICATION)
            "com.medisafe.app.OPEN_NEXT" -> viewModel.openNextReminder()
        }
        val targetReminderId = intent?.getLongExtra(ReminderAlarmReceiver.EXTRA_TARGET_REMINDER_ID, -1L) ?: -1L
        if (targetReminderId != -1L) {
            viewModel.openDetailById(targetReminderId)
        }
    }
}
