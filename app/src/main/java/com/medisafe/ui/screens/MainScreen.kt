package com.medisafe.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.medisafe.data.model.ReminderCategory
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.ui.components.AddEditReminderSheet
import com.medisafe.ui.components.CompactNextUp
import com.medisafe.ui.components.ConfirmRequestDialog
import com.medisafe.ui.components.HeaderAndStats
import com.medisafe.ui.components.HistoryLogTab
import com.medisafe.ui.components.ReminderDetailDialog
import com.medisafe.ui.components.ReminderListItem
import com.medisafe.ui.components.SettingsSheet
import com.medisafe.ui.viewmodel.AppSection
import com.medisafe.ui.viewmodel.MainTab
import com.medisafe.ui.viewmodel.ReminderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ReminderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filteredReminders by viewModel.filteredReminders.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val nextUpcomingReminder by viewModel.nextUpcomingReminder.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val isSheetOpen by viewModel.isSheetOpen.collectAsStateWithLifecycle()
    val sheetReminder by viewModel.sheetReminder.collectAsStateWithLifecycle()
    val detailReminder by viewModel.detailReminder.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val weeklyAdherence by viewModel.weeklyAdherence.collectAsStateWithLifecycle()
    val filteredLogs by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val historyFilters by viewModel.historyFilters.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val undoAction by viewModel.undoAction.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val confirmRequest by viewModel.confirmRequest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSearch by remember { mutableStateOf(false) }
    var hidePermissionBanner by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember {
        mutableStateOf(AlarmScheduler.canScheduleExactAlarms(context))
    }

    LaunchedEffect(lifecycleOwner, section, nextUpcomingReminder?.effectiveTriggerTimeMillis, detailReminder?.id) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            canScheduleExactAlarms = AlarmScheduler.canScheduleExactAlarms(context)
            while (isActive) {
                viewModel.refreshNow()
                val remaining = nextUpcomingReminder?.effectiveTriggerTimeMillis
                    ?.minus(System.currentTimeMillis())
                val interval = when {
                    section != AppSection.HOME && detailReminder == null -> 30_000L
                    remaining == null -> 30_000L
                    remaining <= 15 * 60_000L -> 15_000L
                    else -> 30_000L
                }
                delay(interval)
            }
        }
    }

    LaunchedEffect(userMessage, undoAction) {
        val message = userMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (undoAction != null) "Undo" else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastAction() else viewModel.clearUndo()
        viewModel.consumeUserMessage()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup { json ->
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasNotificationPermission = it }

    val needsAlertPermission = !hidePermissionBanner && (
        (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ||
            (!canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectedIds.isNotEmpty() -> "${selectedIds.size} selected"
                            section == AppSection.HOME -> "MediSafe"
                            section == AppSection.HISTORY -> "History"
                            else -> "Insights"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.requestDeleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        if (section == AppSection.HOME) {
                            IconButton(
                                onClick = { showSearch = !showSearch },
                                modifier = Modifier.testTag("search_toggle")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        IconButton(onClick = { viewModel.openSettings() }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == AppSection.HOME,
                    onClick = { viewModel.setSection(AppSection.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = section == AppSection.HISTORY,
                    onClick = { viewModel.setSection(AppSection.HISTORY) },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = section == AppSection.INSIGHTS,
                    onClick = { viewModel.setSection(AppSection.INSIGHTS) },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("Insights") }
                )
            }
        },
        floatingActionButton = {
            if (section == AppSection.HOME && selectedIds.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val preset = when (selectedTab) {
                            MainTab.MEDICATIONS -> ReminderCategory.MEDICATION
                            MainTab.TASKS -> ReminderCategory.DAILY_TASK
                            MainTab.EVENTS -> ReminderCategory.EVENT
                            else -> null
                        }
                        viewModel.openCreateSheet(preset)
                    },
                    modifier = Modifier.testTag("fab_add_reminder")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (needsAlertPermission) {
                CompactPermissionBar(
                    onEnable = {
                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (!canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                            canScheduleExactAlarms = AlarmScheduler.canScheduleExactAlarms(context)
                        }
                    },
                    onDismiss = { hidePermissionBanner = true }
                )
            }

            when (section) {
                AppSection.HOME -> HomePane(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    selectedTab = selectedTab,
                    filteredReminders = filteredReminders,
                    nextUpcomingReminder = nextUpcomingReminder,
                    currentTimeMillis = currentTimeMillis,
                    onSearch = { viewModel.setSearchQuery(it) },
                    onSelectTab = { viewModel.setSelectedTab(it) },
                    onTake = { viewModel.requestTake(it) },
                    onEdit = { viewModel.openEditSheet(it) },
                    onDelete = { viewModel.requestDelete(it) },
                    onSnooze = { viewModel.snoozeReminder(it, 15) },
                    onOpen = { viewModel.openDetail(it) },
                    selectedIds = selectedIds,
                    onToggleSelect = { viewModel.startOrToggleSelection(it) }
                )
                AppSection.HISTORY -> HistoryLogTab(
                    logs = filteredLogs,
                    filters = historyFilters,
                    onFiltersChange = { viewModel.setHistoryFilters(it) },
                    onClearAll = { viewModel.requestClearHistory() },
                    onDeleteLog = { viewModel.deleteLog(it) },
                    onUndoLog = { viewModel.requestUndoLog(it) }
                )
                AppSection.INSIGHTS -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HeaderAndStats(
                        stats = todayStats,
                        currentTimeMillis = currentTimeMillis,
                        weekly = weeklyAdherence,
                        showTitleBar = false
                    )
                }
            }
        }
    }

    confirmRequest?.let { request ->
        ConfirmRequestDialog(
            request = request,
            onConfirm = { viewModel.confirmPending() },
            onDismiss = { viewModel.dismissConfirm() }
        )
    }

    if (isSheetOpen) {
        AddEditReminderSheet(
            initialItem = sheetReminder,
            onDismiss = { viewModel.closeSheet() },
            onSave = { viewModel.saveReminder(it) }
        )
    }
    if (showSettings) {
        SettingsSheet(
            preferences = viewModel.preferences,
            onDismiss = { viewModel.closeSettings() },
            onExport = { exportLauncher.launch("medisafe-backup.json") },
            onImport = { viewModel.importBackup(it, replaceExisting = false) }
        )
    }
    if (detailReminder != null) {
        ReminderDetailDialog(
            item = detailReminder,
            currentTimeMillis = currentTimeMillis,
            onDismiss = { viewModel.closeDetail() },
            onTakeOrDone = { viewModel.requestTake(it) },
            onSnooze = { item, mins -> viewModel.snoozeReminder(item, mins) },
            onSkip = { viewModel.requestSkip(it) },
            onEdit = {
                viewModel.closeDetail()
                viewModel.openEditSheet(it)
            },
            onDelete = { viewModel.requestDelete(it) }
        )
    }
}

@Composable
private fun CompactPermissionBar(onEnable: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Turn on alerts so reminders fire on time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        TextButton(onClick = onEnable) { Text("Enable", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss) { Text("Later") }
    }
}

@Composable
private fun HomePane(
    showSearch: Boolean,
    searchQuery: String,
    selectedTab: MainTab,
    filteredReminders: List<com.medisafe.data.model.ReminderItem>,
    nextUpcomingReminder: com.medisafe.data.model.ReminderItem?,
    currentTimeMillis: Long,
    onSearch: (String) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onTake: (com.medisafe.data.model.ReminderItem) -> Unit,
    onEdit: (com.medisafe.data.model.ReminderItem) -> Unit,
    onDelete: (com.medisafe.data.model.ReminderItem) -> Unit,
    onSnooze: (com.medisafe.data.model.ReminderItem) -> Unit,
    onOpen: (com.medisafe.data.model.ReminderItem) -> Unit,
    selectedIds: Set<Long>,
    onToggleSelect: (com.medisafe.data.model.ReminderItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showSearch || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Search reminders") },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("search_input"),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(MainTab.ALL, MainTab.MEDICATIONS, MainTab.TASKS, MainTab.EVENTS).forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text("${tab.emoji} ${tab.title}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                )
            }
        }

        if (searchQuery.isBlank() && selectedTab == MainTab.ALL) {
            CompactNextUp(
                reminder = nextUpcomingReminder,
                currentTimeMillis = currentTimeMillis,
                onTakeOrDone = onTake,
                onClick = onOpen
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredReminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Text("📋", fontSize = 28.sp) }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "No matching reminders"
                                else "Nothing scheduled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap + to add a medication, task, or event.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredReminders, key = { it.id }) { item ->
                    ReminderListItem(
                        item = item,
                        currentTimeMillis = currentTimeMillis,
                        onTakeOrDone = onTake,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onSnooze = onSnooze,
                        onClick = onOpen,
                        onLongClick = onToggleSelect,
                        selected = item.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty()
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}
