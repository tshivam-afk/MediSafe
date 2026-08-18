package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LogAction
import com.example.data.model.Priority
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderLog
import com.example.data.repository.ReminderRepository
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val emoji: String) {
    ALL("All", "⚡"),
    MEDICATIONS("Meds", "💊"),
    TASKS("Tasks", "📋"),
    EVENTS("Events", "🗓️"),
    HISTORY("History", "⏱️")
}

data class TodayAdherenceStats(
    val totalToday: Int = 0,
    val completedToday: Int = 0,
    val percentage: Int = 100,
    val upcomingCount: Int = 0
)

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository(
        AppDatabase.getInstance(application).reminderDao(),
        application
    )

    private val _selectedTab = MutableStateFlow(MainTab.ALL)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val _sheetReminder = MutableStateFlow<ReminderItem?>(null)
    val sheetReminder: StateFlow<ReminderItem?> = _sheetReminder.asStateFlow()

    private val _isSheetOpen = MutableStateFlow(false)
    val isSheetOpen: StateFlow<Boolean> = _isSheetOpen.asStateFlow()

    private val _detailReminder = MutableStateFlow<ReminderItem?>(null)
    val detailReminder: StateFlow<ReminderItem?> = _detailReminder.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allReminders: StateFlow<List<ReminderItem>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allLogs: StateFlow<List<ReminderLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredReminders: StateFlow<List<ReminderItem>> = combine(
        allReminders,
        _selectedTab,
        _searchQuery
    ) { reminders, tab, query ->
        reminders.filter { item ->
            val matchesTab = when (tab) {
                MainTab.ALL -> true
                MainTab.MEDICATIONS -> item.categoryEnum == ReminderCategory.MEDICATION
                MainTab.TASKS -> item.categoryEnum == ReminderCategory.DAILY_TASK
                MainTab.EVENTS -> item.categoryEnum == ReminderCategory.EVENT
                MainTab.HISTORY -> true
            }
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.dosageOrDetails.contains(query, ignoreCase = true)
            matchesTab && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nextUpcomingReminder: StateFlow<ReminderItem?> = allReminders
        .combine(_currentTimeMillis) { reminders, _ ->
            reminders
                .filter { it.isActive && !it.isCompleted }
                .minByOrNull { it.effectiveTriggerTimeMillis }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayStats: StateFlow<TodayAdherenceStats> = combine(
        allReminders,
        allLogs,
        _currentTimeMillis
    ) { reminders, logs, _ ->
        val completedIds = logs
            .filter {
                DateTimeUtils.isToday(it.timestampMillis) &&
                    (it.actionEnum == LogAction.TAKEN || it.actionEnum == LogAction.COMPLETED)
            }
            .map { it.reminderId }
            .toSet()
        val scheduledToday = reminders.filter { reminder ->
            reminder.isActive && (
                DateTimeUtils.isToday(reminder.effectiveTriggerTimeMillis) ||
                    DateTimeUtils.isToday(reminder.scheduledTimeMillis) ||
                    completedIds.contains(reminder.id)
                )
        }
        val completedCount = completedIds.size
        val upcoming = scheduledToday.count { !it.isCompleted && !completedIds.contains(it.id) }
        val totalCount = maxOf(scheduledToday.size, completedCount + upcoming)
        val percentage = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 100
        TodayAdherenceStats(
            totalToday = totalCount,
            completedToday = completedCount,
            percentage = percentage.coerceIn(0, 100),
            upcomingCount = upcoming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayAdherenceStats())

    fun refreshNow() {
        _currentTimeMillis.value = System.currentTimeMillis()
    }

    fun setSelectedTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun openCreateSheet(presetCategory: ReminderCategory? = null) {
        val defaultTime = System.currentTimeMillis() + (60 * 60 * 1000)
        val initialItem = ReminderItem(
            title = "",
            category = (presetCategory ?: ReminderCategory.MEDICATION).name,
            dosageOrDetails = "",
            scheduledTimeMillis = defaultTime,
            recurrence = if (presetCategory == ReminderCategory.EVENT) RecurrenceType.ONCE.name else RecurrenceType.DAILY.name,
            priority = Priority.NORMAL.name,
            iconName = when (presetCategory) {
                ReminderCategory.MEDICATION -> "pill"
                ReminderCategory.DAILY_TASK -> "water"
                ReminderCategory.EVENT -> "calendar"
                else -> "pill"
            },
            colorHex = when (presetCategory) {
                ReminderCategory.MEDICATION -> 0xFF0D9488
                ReminderCategory.DAILY_TASK -> 0xFF0284C7
                ReminderCategory.EVENT -> 0xFFF59E0B
                else -> 0xFF8B5CF6
            }
        )
        _sheetReminder.value = initialItem
        _isSheetOpen.value = true
    }

    fun openEditSheet(item: ReminderItem) {
        _sheetReminder.value = item
        _isSheetOpen.value = true
    }

    fun closeSheet() {
        _isSheetOpen.value = false
        _sheetReminder.value = null
    }

    fun openDetail(item: ReminderItem) {
        _detailReminder.value = item
    }

    fun openDetailById(id: Long) {
        viewModelScope.launch {
            runCatching { repository.getReminderById(id) }
                .onSuccess { reminder -> if (reminder != null) _detailReminder.value = reminder }
                .onFailure { _userMessage.value = "Couldn't open that reminder." }
        }
    }

    fun closeDetail() {
        _detailReminder.value = null
    }

    fun saveReminder(item: ReminderItem) {
        viewModelScope.launch {
            runCatching {
                if (item.id == 0L) repository.saveReminder(item) else repository.updateReminder(item)
            }.onSuccess {
                closeSheet()
                if (_detailReminder.value?.id == item.id) {
                    _detailReminder.value = repository.getReminderById(item.id) ?: item
                }
            }.onFailure {
                _userMessage.value = "Couldn't save reminder. Please try again."
            }
        }
    }

    fun deleteReminder(item: ReminderItem) {
        viewModelScope.launch {
            runCatching { repository.deleteReminder(item) }
                .onSuccess {
                    if (_detailReminder.value?.id == item.id) _detailReminder.value = null
                    closeSheet()
                }
                .onFailure { _userMessage.value = "Couldn't delete reminder." }
        }
    }

    fun markDoneOrTaken(item: ReminderItem) {
        viewModelScope.launch {
            runCatching { repository.markDoneOrTaken(item) }
                .onSuccess {
                    if (_detailReminder.value?.id == item.id) {
                        _detailReminder.value = repository.getReminderById(item.id)
                    }
                }
                .onFailure { _userMessage.value = "Couldn't update reminder." }
        }
    }

    fun snoozeReminder(item: ReminderItem, minutes: Int = 15) {
        viewModelScope.launch {
            runCatching { repository.snoozeReminder(item, minutes) }
                .onSuccess {
                    if (_detailReminder.value?.id == item.id) {
                        _detailReminder.value = repository.getReminderById(item.id)
                    }
                }
                .onFailure { _userMessage.value = "Couldn't snooze reminder." }
        }
    }

    fun skipReminder(item: ReminderItem) {
        viewModelScope.launch {
            runCatching { repository.skipReminder(item) }
                .onSuccess {
                    if (_detailReminder.value?.id == item.id) {
                        _detailReminder.value = repository.getReminderById(item.id)
                    }
                }
                .onFailure { _userMessage.value = "Couldn't skip reminder." }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            runCatching { repository.clearLogs() }
                .onFailure { _userMessage.value = "Couldn't clear history." }
        }
    }

    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteLogById(logId) }
                .onFailure { _userMessage.value = "Couldn't delete history item." }
        }
    }
}
