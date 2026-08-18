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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    private val repository: ReminderRepository

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

    init {
        val db = AppDatabase.getInstance(application)
        repository = ReminderRepository(db.reminderDao(), application)

        // Seed sample reminders if database is empty on first launch
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Live ticker updating current time every 1 second for live countdown
        viewModelScope.launch {
            while (isActive) {
                _currentTimeMillis.value = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val allReminders: StateFlow<List<ReminderItem>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<ReminderLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered list based on current Tab & Search Query
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
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                item.title.contains(query, ignoreCase = true) ||
                        item.dosageOrDetails.contains(query, ignoreCase = true)
            }
            matchesTab && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Next upcoming reminder (earliest active uncompleted item)
    val nextUpcomingReminder: StateFlow<ReminderItem?> = allReminders
        .combine(_currentTimeMillis) { reminders, _ ->
            reminders
                .filter { it.isActive && !it.isCompleted }
                .minByOrNull { it.effectiveTriggerTimeMillis }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Today's Adherence Stats
    val todayStats: StateFlow<TodayAdherenceStats> = combine(
        allReminders,
        allLogs
    ) { reminders, logs ->
        val todayItems = reminders.filter { DateTimeUtils.isToday(it.scheduledTimeMillis) }
        val completedCount = todayItems.count { it.isCompleted }
        val totalCount = todayItems.size
        val percentage = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 100
        val upcoming = todayItems.count { !it.isCompleted }

        TodayAdherenceStats(
            totalToday = totalCount,
            completedToday = completedCount,
            percentage = percentage,
            upcomingCount = upcoming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayAdherenceStats())

    fun setSelectedTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openCreateSheet(presetCategory: ReminderCategory? = null) {
        val defaultTime = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour from now
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

    fun closeDetail() {
        _detailReminder.value = null
    }

    fun saveReminder(item: ReminderItem) {
        viewModelScope.launch {
            if (item.id == 0L) {
                repository.saveReminder(item)
            } else {
                repository.updateReminder(item)
            }
            closeSheet()
            // If the detail modal was showing this item, update it
            if (_detailReminder.value?.id == item.id) {
                _detailReminder.value = item
            }
        }
    }

    fun deleteReminder(item: ReminderItem) {
        viewModelScope.launch {
            repository.deleteReminder(item)
            if (_detailReminder.value?.id == item.id) {
                _detailReminder.value = null
            }
            closeSheet()
        }
    }

    fun markDoneOrTaken(item: ReminderItem) {
        viewModelScope.launch {
            repository.markDoneOrTaken(item)
            if (_detailReminder.value?.id == item.id) {
                _detailReminder.value = repository.getReminderById(item.id)
            }
        }
    }

    fun snoozeReminder(item: ReminderItem, minutes: Int = 15) {
        viewModelScope.launch {
            repository.snoozeReminder(item, minutes)
            if (_detailReminder.value?.id == item.id) {
                _detailReminder.value = repository.getReminderById(item.id)
            }
        }
    }

    fun skipReminder(item: ReminderItem) {
        viewModelScope.launch {
            repository.skipReminder(item)
            if (_detailReminder.value?.id == item.id) {
                _detailReminder.value = repository.getReminderById(item.id)
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            repository.deleteLogById(logId)
        }
    }
}
