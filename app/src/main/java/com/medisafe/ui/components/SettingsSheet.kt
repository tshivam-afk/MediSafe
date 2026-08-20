package com.medisafe.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    var alarmReliability by remember { mutableStateOf(preferences.alarmReliabilityEnabled) }
    val onVacation = vacationUntilMillis > System.currentTimeMillis()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "MediSafe v$currentVersion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "Updates",
                subtitle = "Keep MediSafe up to date."
            ) {
                SettingToggleRow(
                    title = "Auto-update check",
                    subtitle = "Only after MediSafe is swiped away from Recents — not on every resume.",
                    checked = autoUpdate,
                    onCheckedChange = {
                        autoUpdate = it
                        preferences.autoUpdateEnabled = it
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.Alarm,
                title = "Alarm Reliability",
                subtitle = "Full-screen ringing alarms for the reminders you choose.",
                trailing = {
                    Switch(
                        checked = alarmReliability,
                        onCheckedChange = {
                            alarmReliability = it
                            preferences.alarmReliabilityEnabled = it
                        }
                    )
                }
            ) {
                AlarmReliabilityContent(preferences, enabled = alarmReliability)
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.PauseCircle,
                title = "Pause Alerts",
                subtitle = if (onVacation)
                    "Muted until ${com.medisafe.util.DateTimeUtils.formatDateTime(vacationUntilMillis)}"
                else "Mute every reminder without deleting them."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 day", 3 to "3 days", 7 to "7 days").forEach { (days, label) ->
                        OutlinedButton(
                            onClick = { onVacationDays(days) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                }
                if (onVacation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onVacationDays(0) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Resume alerts now")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.SaveAlt,
                title = "Backup & Restore",
                subtitle = "Export reminders to a JSON file, or import a previous backup."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Text("Export")
                    }
                    ImportBackupButton(onImport = onImport, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.Lock,
                title = "Privacy Lock",
                subtitle = if (preferences.hasPin) "PIN is on. Enter a new one to change it."
                else "Set a 4–8 digit PIN to hide medications on the lock screen."
            ) {
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
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    SettingToggleRow(
                        title = "Unlock with biometrics",
                        subtitle = "Use your fingerprint or face instead of the PIN.",
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
 * A rounded, tinted card that visually groups one settings feature. Icon-led header with
 * optional trailing slot (used for the Alarm Reliability master switch), a hairline
 * divider, then the feature's controls.
 */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailing?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    it()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.6f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
    }
}

/**
 * Controls for making alarms audible when the screen is off, plus the battery-optimisation
 * escape hatch that most OEM ROMs require before exact alarms are trusted.
 *
 * Everything below the master switch is grayed out until Alarm Reliability is turned on,
 * and every option defaults to off.
 */
@Composable
private fun AlarmReliabilityContent(preferences: AppPreferences, enabled: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var forceVolume by remember { mutableStateOf(preferences.forceAlarmVolume) }
    var gradual by remember { mutableStateOf(preferences.gradualAlarmVolume) }
    var escalation by remember { mutableStateOf(preferences.escalationMinutes) }
    var timeout by remember { mutableStateOf(preferences.alarmTimeoutMinutes) }

    var batteryUnrestricted by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    Text(
        if (enabled)
            "Reminders with \"Ring like an alarm\" set ring full-screen and loop until " +
                "answered — audible even when the screen is off or the phone is on silent."
        else
            "Off. Turn this on to unlock \"Ring like an alarm\" when adding or editing a " +
                "reminder, plus re-ring escalation for unanswered alarms.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.7f)
    )
    Spacer(modifier = Modifier.height(10.dp))

    SettingToggleRow(
        title = "Override silent mode",
        subtitle = "Temporarily raises alarm volume, then restores it.",
        checked = forceVolume,
        onCheckedChange = {
            forceVolume = it
            preferences.forceAlarmVolume = it
        },
        enabled = enabled
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingToggleRow(
        title = "Fade alarm in",
        subtitle = "Starts quiet and ramps up instead of blasting instantly.",
        checked = gradual,
        onCheckedChange = {
            gradual = it
            preferences.gradualAlarmVolume = it
        },
        enabled = enabled
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        "Ring for $timeout min before giving up",
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(6.dp))
    ChoiceChipRow(
        options = listOf(2 to "2 m", 5 to "5 m", 10 to "10 m"),
        selected = timeout,
        onSelect = {
            timeout = it
            preferences.alarmTimeoutMinutes = it
        },
        enabled = enabled
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        if (escalation <= 0) "Re-ring if ignored: off"
        else "Re-ring if ignored: every $escalation min",
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(6.dp))
    ChoiceChipRow(
        options = listOf(0 to "Off", 5 to "5 m", 10 to "10 m", 15 to "15 m"),
        selected = escalation,
        onSelect = {
            escalation = it
            preferences.escalationMinutes = it
        },
        enabled = enabled
    )

    Spacer(modifier = Modifier.height(12.dp))
    if (batteryUnrestricted) {
        Text(
            "✓ Battery optimisation is off for MediSafe — alarms won't be delayed.",
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        Text(
            "Android may delay alarms while the phone sleeps. Allow unrestricted battery use " +
                "so alarms always fire on time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                requestIgnoreBatteryOptimizations(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) { Text("Allow unrestricted battery use") }
    }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { openNotificationSettings(context) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) { Text("Open notification & alarm settings") }
}

/** A row of small pill choices; the selected value is filled to stand out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChipRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(onClick = { }, enabled = enabled) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, enabled = enabled) { Text(label) }
            }
        }
    }
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
private fun ImportBackupButton(onImport: (String) -> Unit, modifier: Modifier = Modifier) {
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
        modifier = modifier
    ) {
        Text("Import")
    }
}
