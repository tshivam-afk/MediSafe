package com.medisafe.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.medisafe.data.prefs.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
    currentVersion: String,
    checkingUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    vacationUntilMillis: Long,
    onVacationDays: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var biometric by remember { mutableStateOf(preferences.biometricEnabled) }
    var autoUpdate by remember { mutableStateOf(preferences.autoUpdateEnabled) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("UPDATES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You're on v$currentVersion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Auto-update check", fontWeight = FontWeight.Medium)
                    Text(
                        "Only after MediSafe is swiped away from Recents — not on every resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = {
                        autoUpdate = it
                        preferences.autoUpdateEnabled = it
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCheckForUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !checkingUpdate
            ) {
                if (checkingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Check for updates")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            AlarmReliabilitySection(preferences)

            Spacer(modifier = Modifier.height(20.dp))
            Text("PAUSE ALERTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (vacationUntilMillis > System.currentTimeMillis())
                    "Muted until ${com.medisafe.util.DateTimeUtils.formatDateTime(vacationUntilMillis)}"
                else "Mute every reminder without deleting them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1 day", 3 to "3 days", 7 to "7 days").forEach { (days, label) ->
                    OutlinedButton(onClick = { onVacationDays(days) }) { Text(label) }
                }
            }
            if (vacationUntilMillis > System.currentTimeMillis()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onVacationDays(0) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Resume alerts now")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("BACKUP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export reminders")
            }
            Spacer(modifier = Modifier.height(8.dp))
            ImportBackupButton(onImport = onImport)
            Spacer(modifier = Modifier.height(20.dp))
            Text("PRIVACY LOCK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (preferences.hasPin) "PIN is on. Enter a new one to change it."
                else "Set a 4–8 digit PIN to hide medications on the lock screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                label = { Text("New PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(8) },
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            pinMessage?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (pin.length < 4 || pin != confirmPin) {
                        pinMessage = "PINs must match and be at least 4 digits."
                    } else {
                        preferences.setPin(pin)
                        pin = ""
                        confirmPin = ""
                        pinMessage = "PIN saved."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.isNotBlank()
            ) {
                Text(if (preferences.hasPin) "Update PIN" else "Set PIN")
            }
            if (preferences.hasPin) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        preferences.clearPin()
                        biometric = false
                        pinMessage = "Lock removed."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove lock")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock with biometrics", modifier = Modifier.weight(1f))
                    Switch(
                        checked = biometric,
                        onCheckedChange = {
                            biometric = it
                            preferences.biometricEnabled = it
                        }
                    )
                }
            }
        }
    }
}

/**
 * Controls for making alarms audible when the screen is off, plus the battery-optimisation
 * escape hatch that most OEM ROMs require before exact alarms are trusted.
 */
@Composable
private fun AlarmReliabilitySection(preferences: AppPreferences) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var forceVolume by remember { mutableStateOf(preferences.forceAlarmVolume) }
    var gradual by remember { mutableStateOf(preferences.gradualAlarmVolume) }
    var escalation by remember { mutableStateOf(preferences.escalationMinutes) }
    var timeout by remember { mutableStateOf(preferences.alarmTimeoutMinutes) }

    var batteryUnrestricted by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    Text(
        "ALARM RELIABILITY",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "High and Urgent reminders always ring like an alarm — full screen, looping, and audible " +
            "even when the screen is off or the phone is on silent.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Override silent mode", fontWeight = FontWeight.Medium)
            Text(
                "Temporarily raises alarm volume, then restores it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = forceVolume,
            onCheckedChange = {
                forceVolume = it
                preferences.forceAlarmVolume = it
            }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Fade alarm in", fontWeight = FontWeight.Medium)
            Text(
                "Starts quiet and ramps up instead of blasting instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = gradual,
            onCheckedChange = {
                gradual = it
                preferences.gradualAlarmVolume = it
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Ring for $timeout min before giving up", style = MaterialTheme.typography.bodySmall)
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(2, 5, 10).forEach { minutes ->
            OutlinedButton(onClick = {
                timeout = minutes
                preferences.alarmTimeoutMinutes = minutes
            }) { Text("$minutes m") }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        if (escalation <= 0) "Re-ring if ignored: off"
        else "Re-ring if ignored: every $escalation min",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Off", 5 to "5 m", 10 to "10 m", 15 to "15 m").forEach { (minutes, label) ->
            OutlinedButton(onClick = {
                escalation = minutes
                preferences.escalationMinutes = minutes
            }) { Text(label) }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    if (batteryUnrestricted) {
        Text(
            "✓ Battery optimisation is off for MediSafe — alarms won't be delayed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        Text(
            "Android may delay alarms while the phone sleeps. Allow unrestricted battery use " +
                "so alarms always fire on time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                requestIgnoreBatteryOptimizations(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow unrestricted battery use") }
    }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { openNotificationSettings(context) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Open notification & alarm settings") }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@android.annotation.SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        // Some ROMs hide the direct dialog; fall back to the general battery settings list.
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ImportBackupButton(onImport: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.let(onImport)
    }
    OutlinedButton(
        onClick = { readLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Import backup")
    }
}
