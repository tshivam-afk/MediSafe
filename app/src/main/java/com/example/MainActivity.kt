package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.notifications.ReminderAlarmReceiver
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MediSafeTheme
import com.example.ui.viewmodel.ReminderViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            MediSafeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val targetReminderId = intent?.getLongExtra(ReminderAlarmReceiver.EXTRA_TARGET_REMINDER_ID, -1L) ?: -1L
        if (targetReminderId != -1L) {
            viewModel.openDetailById(targetReminderId)
        }
    }
}
