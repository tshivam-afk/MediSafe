package com.medisafe.ui.screens

import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisafe.notifications.AlarmScheduler
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val (emoji, title, body) = when (page) {
                0 -> Triple("💊", "Welcome to MediSafe", "Keep medications, daily tasks, and events on one local schedule. Nothing leaves this phone.")
                1 -> Triple("🔔", "Allow timely alerts", "Notifications and exact alarms are how MediSafe fires on time, even if the app is closed.")
                else -> Triple("✨", "Create your first reminder", "Add a pill time, a daily task, or an appointment. You can export a backup anytime from Settings.")
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(emoji, fontSize = 56.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = {
                when (pagerState.currentPage) {
                    0 -> scope.launch { pagerState.animateScrollToPage(1) }
                    1 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExactAlarms(context)) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        }
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                    else -> onFinished()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = when (pagerState.currentPage) {
                    0 -> "Continue"
                    1 -> "Allow alerts"
                    else -> "Get started"
                },
                fontWeight = FontWeight.Bold
            )
        }
        if (pagerState.currentPage < 2) {
            TextButton(
                onClick = onFinished,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Skip")
            }
        }
    }
}
