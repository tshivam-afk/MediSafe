package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.notifications.AlarmScheduler
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReminderViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Reschedule alarms on start to ensure state is synchronized
        AlarmScheduler.rescheduleAllActive(applicationContext)

        // If opened from notification with a specific reminder target
        val targetReminderId = intent?.getLongExtra("EXTRA_TARGET_REMINDER_ID", -1L) ?: -1L
        if (targetReminderId != -1L) {
            lifecycleScope.launch {
                val reminder = viewModel.allReminders.value.firstOrNull { it.id == targetReminderId }
                if (reminder != null) {
                    viewModel.openDetail(reminder)
                }
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

