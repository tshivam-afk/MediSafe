package com.medisafe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.DayAdherence
import com.medisafe.data.model.HomeSort
import com.medisafe.data.model.LogAction
import com.medisafe.data.model.Priority
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.model.ReminderLog
import com.medisafe.data.prefs.AppPreferences
import com.medisafe.data.repository.ReminderRepository
import com.medisafe.update.AppUpdater
import com.medisafe.update.UpdateState
import com.medisafe.util.DateTimeUtils
import com.medisafe.util.DoseTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppSection(val title: String) {
    HOME("Home"),
    HISTORY("History"),
    INSIGHTS("Insights")
}

enum class MainTab(val title: String, val emoji: String) {
    ALL("All", "⚡"),
    MEDICATIONS("Meds", "💊"),
    TASKS("Tasks", "📋"),
    EVENTS("Events", "🗓️")
}

data class TodayAdherenceStats(
    val totalToday: Int = 0,
    val completedToday: Int = 0,
    val percentage: Int = 100,
    val upcomingCount: Int = 0,
    val missedToday: Int = 0
)

data class HistoryFilters(
    val query: String = "",
    val category: ReminderCategory? = null,
    val action: LogAction? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null
)

sealed class UndoAction {
    data class Deleted(val items: List<Pair<ReminderItem, List<ReminderLog>>>) : UndoAction()
    data class Changed(val previous: ReminderItem, val label: String) : UndoAction()
}

sealed class ConfirmRequest {
    data class Delete(val items: List<ReminderItem>) : ConfirmRequest()
    data class Take(val item: ReminderItem) : ConfirmRequest()
    data class BatchTake(val items: List<ReminderItem>) : ConfirmRequest()
    data class Skip(val item: ReminderItem) : ConfirmRequest()
    data class UndoLog(val log: ReminderLog) : ConfirmRequest()
    data class Refill(val item: ReminderItem) : ConfirmRequest()
    data object ClearHistory : ConfirmRequest()
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository(
        AppDatabase.getInstance(application).reminderDao(),
        application
    )
    val preferences = AppPreferences(application)
    private val updater = AppUpdater(application, preferences, viewModelScope)
    val updateState: StateFlow<UpdateState> = updater.state

    private val _section = MutableStateFlow(AppSection.HOME)
    val section: StateFlow<AppSection> = _section.asStateFlow()

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

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    private val _historyFilters = MutableStateFlow(HistoryFilters())
    val historyFilters: StateFlow<HistoryFilters> = _historyFilters.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showOnboarding = MutableStateFlow(!preferences.onboardingComplete)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _isLocked = MutableStateFlow(preferences.hasPin)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _confirmRequest = MutableStateFlow<ConfirmRequest?>(null)
    val confirmRequest: StateFlow<ConfirmRequest?> = _confirmRequest.asStateFlow()

    private var takeInFlight = false

    private val _homeSort = MutableStateFlow(
        runCatching { HomeSort.valueOf(preferences.homeSort) }.getOrDefault(HomeSort.NEXT_DUE)
    )
    val homeSort: StateFlow<HomeSort> = _homeSort.asStateFlow()

    private val _vacationUntil = MutableStateFlow(preferences.vacationUntilMillis)
    val vacationUntil: StateFlow<Long> = _vacationUntil.asStateFlow()

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
            }
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.dosageOrDetails.contains(query, ignoreCase = true)
            matchesTab && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nextUpcomingReminder: StateFlow<ReminderItem?> = allReminders
        .map { reminders ->
            reminders
                .filter { it.isActive && !it.isCompleted }
                .minByOrNull { it.effectiveTriggerTimeMillis }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayStats: StateFlow<TodayAdherenceStats> = combine(
        allReminders,
        allLogs
    ) { reminders, logs ->
        val todayLogs = logs.filter { DateTimeUtils.isToday(it.timestampMillis) }
        val completedIds = todayLogs
            .filter { it.actionEnum == LogAction.TAKEN || it.actionEnum == LogAction.COMPLETED }
            .map { it.reminderId }
            .toSet()
        val missedToday = todayLogs.count { it.actionEnum == LogAction.MISSED }
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
            upcomingCount = upcoming,
            missedToday = missedToday
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayAdherenceStats())

    val weeklyAdherence: StateFlow<List<DayAdherence>> = allLogs
        .map { logs -> repository.weeklyAdherence(logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredLogs: StateFlow<List<ReminderLog>> = combine(allLogs, _historyFilters) { logs, filters ->
        logs.filter { log ->
            val matchesQuery = filters.query.isBlank() ||
                log.reminderTitle.contains(filters.query, ignoreCase = true) ||
                log.note.contains(filters.query, ignoreCase = true)
            val matchesCategory = filters.category == null || log.category == filters.category.name
            val matchesAction = filters.action == null || log.actionEnum == filters.action
            val matchesFrom = filters.fromMillis == null || log.timestampMillis >= filters.fromMillis
            val matchesTo = filters.toMillis == null || log.timestampMillis <= filters.toMillis
            matchesQuery && matchesCategory && matchesAction && matchesFrom && matchesTo
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { repository.scanAndLogMissed() }
        }
    }

    fun refreshNow() {
        _currentTimeMillis.value = System.currentTimeMillis()
    }

    fun setSection(section: AppSection) {
        _section.value = section
        if (section != AppSection.HOME) {
            _searchQuery.value = ""
            clearSelection()
        }
    }

    fun startOrToggleSelection(item: ReminderItem) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(item.id)) current - item.id else current + item.id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun requestDelete(item: ReminderItem) {
        _confirmRequest.value = ConfirmRequest.Delete(listOf(item))
    }

    fun requestDeleteSelected() {
        val items = allReminders.value.filter { it.id in _selectedIds.value }
        if (items.isNotEmpty()) {
            _confirmRequest.value = ConfirmRequest.Delete(items)
        }
    }

    fun requestTake(item: ReminderItem) {
        _confirmRequest.value = ConfirmRequest.Take(item)
    }

    fun requestBatchTake(items: List<ReminderItem>) {
        if (items.size == 1) {
            requestTake(items.first())
        } else if (items.isNotEmpty()) {
            _confirmRequest.value = ConfirmRequest.BatchTake(items)
        }
    }

    fun requestRefill(item: ReminderItem) {
        _confirmRequest.value = ConfirmRequest.Refill(item)
    }

    fun duplicateReminder(item: ReminderItem) {
        openEditSheet(item.copy(id = 0L, title = "Copy of ${item.title}", lastAcknowledgedMillis = null, isCompleted = false, completedAtMillis = null))
    }

    fun setHomeSort(sort: HomeSort) {
        _homeSort.value = sort
        preferences.homeSort = sort.name
    }

    fun setVacationDays(days: Int) {
        val until = if (days <= 0) 0L else System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        preferences.vacationUntilMillis = until
        _vacationUntil.value = until
        com.medisafe.notifications.AlarmScheduler.scheduleVacationResume(getApplication(), until)
        com.medisafe.notifications.AlarmScheduler.rescheduleAllActive(getApplication())
        _userMessage.value = if (days <= 0) "Alerts resumed" else "Alerts paused for $days day${if (days == 1) "" else "s"}"
    }

    fun sameSlotBatch(around: ReminderItem?, now: Long = System.currentTimeMillis()): List<ReminderItem> {
        if (around == null) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = around.effectiveTriggerTimeMillis }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        return allReminders.value.filter { item ->
            if (item.id == around.id || item.isCompleted || !item.isActive || item.isPrn) return@filter false
            val other = java.util.Calendar.getInstance().apply { timeInMillis = item.effectiveTriggerTimeMillis }
            other.get(java.util.Calendar.HOUR_OF_DAY) == hour &&
                other.get(java.util.Calendar.MINUTE) == minute &&
                kotlin.math.abs(item.effectiveTriggerTimeMillis - around.effectiveTriggerTimeMillis) < 12L * 60L * 60L * 1000L
        }.let { listOf(around) + it }
    }

    fun requestSkip(item: ReminderItem) {
        _confirmRequest.value = ConfirmRequest.Skip(item)
    }

    fun requestUndoLog(log: ReminderLog) {
        _confirmRequest.value = ConfirmRequest.UndoLog(log)
    }

    fun requestClearHistory() {
        _confirmRequest.value = ConfirmRequest.ClearHistory
    }

    fun dismissConfirm() {
        _confirmRequest.value = null
    }

    fun confirmPending(extra: String = "") {
        when (val request = _confirmRequest.value) {
            is ConfirmRequest.Delete -> deleteReminders(request.items)
            is ConfirmRequest.Take -> markDoneOrTaken(request.item, extra)
            is ConfirmRequest.BatchTake -> markBatch(request.items, extra)
            is ConfirmRequest.Skip -> skipReminder(request.item)
            is ConfirmRequest.UndoLog -> undoHistoryLog(request.log)
            is ConfirmRequest.Refill -> refillReminder(request.item, extra.toIntOrNull() ?: 10)
            is ConfirmRequest.ClearHistory -> clearAllLogs()
            null -> Unit
        }
        _confirmRequest.value = null
    }

    fun setSelectedTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setHistoryFilters(filters: HistoryFilters) {
        _historyFilters.value = filters
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun currentAppVersion(): String = updater.currentVersion()

    fun checkForUpdate(manual: Boolean) {
        updater.check(manual)
    }

    fun downloadAndInstallUpdate() {
        updater.downloadAndInstall()
    }

    fun dismissUpdate() {
        updater.dismiss()
    }

    fun consumeUpdateTransient() {
        updater.consumeTransient()
    }

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun completeOnboarding() {
        preferences.onboardingComplete = true
        _showOnboarding.value = false
    }

    fun unlock() {
        _isLocked.value = false
    }

    fun lockIfNeeded() {
        if (preferences.hasPin) _isLocked.value = true
    }

    fun verifyPin(pin: String): Boolean {
        val ok = preferences.verifyPin(pin)
        if (ok) _isLocked.value = false
        return ok
    }

    fun openCreateSheet(presetCategory: ReminderCategory? = null) {
        val defaultTime = System.currentTimeMillis() + (60 * 60 * 1000)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = defaultTime }
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
            },
            doseTimes = DoseTimes.format(listOf(cal.get(java.util.Calendar.HOUR_OF_DAY) to cal.get(java.util.Calendar.MINUTE)))
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

    fun openNextReminder() {
        nextUpcomingReminder.value?.let { openDetail(it) }
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
        requestDelete(item)
    }

    private fun deleteReminders(items: List<ReminderItem>) {
        viewModelScope.launch {
            runCatching { repository.deleteReminders(items) }
                .onSuccess { removed ->
                    if (items.any { it.id == _detailReminder.value?.id }) _detailReminder.value = null
                    closeSheet()
                    clearSelection()
                    _undoAction.value = UndoAction.Deleted(removed)
                    _userMessage.value = if (removed.size == 1) "Reminder deleted" else "${removed.size} reminders deleted"
                }
                .onFailure { _userMessage.value = "Couldn't delete reminder." }
        }
    }

    fun markDoneOrTaken(item: ReminderItem, note: String = "") {
        if (takeInFlight) return
        takeInFlight = true
        viewModelScope.launch {
            runCatching { repository.markDoneOrTaken(item, note) }
                .onSuccess { updated ->
                    if (_detailReminder.value?.id == item.id) _detailReminder.value = updated
                    if (updated.lastAcknowledgedMillis != item.lastAcknowledgedMillis) {
                        _undoAction.value = UndoAction.Changed(item, "Marked done")
                        _userMessage.value = if (updated.needsRefill) {
                            "Logged. Only ${updated.pillsRemaining} left — refill soon."
                        } else {
                            "Marked done"
                        }
                    }
                }
                .onFailure { _userMessage.value = "Couldn't update reminder." }
            takeInFlight = false
        }
    }

    /** Takes every reminder that shares the same time slot in one go. */
    private fun markBatch(items: List<ReminderItem>, note: String = "") {
        if (items.isEmpty()) return
        if (takeInFlight) return
        takeInFlight = true
        viewModelScope.launch {
            var done = 0
            items.forEach { item ->
                runCatching { repository.markDoneOrTaken(item, note) }
                    .onSuccess { updated ->
                        if (_detailReminder.value?.id == item.id) _detailReminder.value = updated
                        if (updated.lastAcknowledgedMillis != item.lastAcknowledgedMillis) done++
                    }
            }
            clearSelection()
            _userMessage.value = when (done) {
                0 -> "Nothing to log right now."
                1 -> "Marked done"
                else -> "Marked $done reminders done"
            }
            takeInFlight = false
        }
    }

    /** Adds pills back to a medication's remaining count. */
    private fun refillReminder(item: ReminderItem, amount: Int) {
        viewModelScope.launch {
            runCatching { repository.refillPills(item, amount) }
                .onSuccess { updated ->
                    if (_detailReminder.value?.id == item.id) _detailReminder.value = updated
                    _userMessage.value = "Refilled · ${updated.pillsRemaining} remaining"
                }
                .onFailure { _userMessage.value = "Couldn't refill reminder." }
        }
    }

    fun snoozeReminder(item: ReminderItem, minutes: Int = 15) {
        viewModelScope.launch {
            runCatching { repository.snoozeReminder(item, minutes) }
                .onSuccess { updated ->
                    if (_detailReminder.value?.id == item.id) _detailReminder.value = updated
                    _undoAction.value = UndoAction.Changed(item, "Snoozed")
                    _userMessage.value = "Snoozed $minutes min"
                }
                .onFailure { _userMessage.value = "Couldn't snooze reminder." }
        }
    }

    fun skipReminder(item: ReminderItem) {
        viewModelScope.launch {
            runCatching { repository.skipReminder(item) }
                .onSuccess { updated ->
                    if (_detailReminder.value?.id == item.id) _detailReminder.value = updated
                    _undoAction.value = UndoAction.Changed(item, "Skipped")
                    _userMessage.value = "Skipped this occurrence"
                }
                .onFailure { _userMessage.value = "Couldn't skip reminder." }
        }
    }

    fun undoLastAction() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            runCatching {
                when (action) {
                    is UndoAction.Deleted -> action.items.forEach { (item, logs) ->
                        repository.restoreDeleted(item, logs)
                    }
                    is UndoAction.Changed -> repository.restorePreviousState(action.previous)
                }
            }.onSuccess {
                _undoAction.value = null
                _userMessage.value = "Undone"
            }.onFailure {
                _userMessage.value = "Couldn't undo."
            }
        }
    }

    fun clearUndo() {
        _undoAction.value = null
    }

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportBackup() }
                .onSuccess(onReady)
                .onFailure { _userMessage.value = "Couldn't export backup." }
        }
    }

    fun importBackup(raw: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            runCatching { repository.importBackup(raw, replaceExisting) }
                .onSuccess { count -> _userMessage.value = "Imported $count reminders" }
                .onFailure { _userMessage.value = "Couldn't import backup. Check the file." }
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

    fun undoHistoryLog(log: ReminderLog) {
        viewModelScope.launch {
            runCatching { repository.undoHistoryLog(log) }
                .onSuccess { _userMessage.value = "History entry undone" }
                .onFailure { _userMessage.value = "Couldn't undo that log." }
        }
    }
}
