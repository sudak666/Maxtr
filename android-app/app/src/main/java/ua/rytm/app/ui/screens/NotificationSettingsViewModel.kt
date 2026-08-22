package ua.rytm.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.PushRepository
import ua.rytm.app.R

// Mirrors js/notifications.js's 4 independent toggles (toggleReminders()/
// toggleBudgetAlerts()/toggleRecurringAlerts()/toggleDebtAlerts()) plus the
// reminder-time <select> pair (populateNotifTimeSelects()/
// updateNotifTimeFromSelects()) — the granular settings step 27's own doc
// comment disclosed as a separate follow-up (see PushRepository/
// NotificationSettingsSheet). No Room table backs this domain (unlike every
// other manager sheet in this app) — notifSettings only has meaning as a
// per-profile Firestore field the server sweep reads directly (it lives
// inside the same `finance` doc every other profile-scoped domain does, see
// step 30's ProfileDocNames.kt), so a one-shot load + optimistic-local-
// write-through pattern is used instead of the Room+Flow pipeline every
// synced domain otherwise gets.
class NotificationSettingsViewModel(
    private val uid: String,
    private val repository: PushRepository,
    private val profileId: String = DEFAULT_PROFILE_ID,
) : ViewModel() {

    companion object {
        fun factory(uid: String, repository: PushRepository, profileId: String = DEFAULT_PROFILE_ID) = viewModelFactory {
            initializer { NotificationSettingsViewModel(uid, repository, profileId) }
        }
    }

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    var dailyReminderEnabled by mutableStateOf(false)
        private set
    var reminderHour by mutableStateOf("21")
        private set
    var reminderMinute by mutableStateOf("00")
        private set
    var budgetAlerts by mutableStateOf(false)
        private set
    var recurringAlerts by mutableStateOf(false)
        private set
    var debtAlerts by mutableStateOf(false)
        private set

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        loading = true
        runCatching { repository.getNotifSettings(uid, profileId) }
            .onSuccess { s ->
                val (h, m) = s.time.split(":").let { it.getOrElse(0) { "21" } to it.getOrElse(1) { "00" } }
                dailyReminderEnabled = s.enabled
                reminderHour = h
                reminderMinute = m
                budgetAlerts = s.budgetAlerts
                recurringAlerts = s.recurringAlerts
                debtAlerts = s.debtAlerts
            }
            .onFailure { errorMessageRes = R.string.notifications_load_failed }
        loading = false
    }

    fun consumeError() { errorMessageRes = null }

    private fun save(write: suspend () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            runCatching { write() }.onFailure {
                errorMessageRes = R.string.notifications_save_failed
                load().join()
            }
            saving = false
        }
    }

    private fun currentTime() = "$reminderHour:$reminderMinute"

    // Named onXChanged, not setX — a plain setX name would collide with the
    // Kotlin-compiler-synthesized property setter for the same-named `var`
    // above (identical JVM signature, a real "platform declaration clash"
    // compile error caught immediately, not a style preference).
    fun onDailyReminderChanged(enabled: Boolean) {
        if (saving) return
        dailyReminderEnabled = enabled
        save { repository.setDailyReminder(uid, enabled, currentTime(), profileId) }
    }

    fun onReminderTimeChanged(hour: String, minute: String) {
        if (saving) return
        reminderHour = hour
        reminderMinute = minute
        // Only writes if the reminder is actually on — matches
        // js/notifications.js's updateNotifTimeFromSelects(), which is only
        // ever reachable while the reminder checkbox is checked (the time
        // <select>s are inside the same conditionally-shown block).
        if (dailyReminderEnabled) save { repository.setDailyReminder(uid, true, "$hour:$minute", profileId) }
    }

    fun onBudgetAlertsChanged(enabled: Boolean) {
        if (saving) return
        budgetAlerts = enabled
        save { repository.setBudgetAlerts(uid, enabled, profileId) }
    }

    fun onRecurringAlertsChanged(enabled: Boolean) {
        if (saving) return
        recurringAlerts = enabled
        save { repository.setRecurringAlerts(uid, enabled, profileId) }
    }

    fun onDebtAlertsChanged(enabled: Boolean) {
        if (saving) return
        debtAlerts = enabled
        save { repository.setDebtAlerts(uid, enabled, profileId) }
    }
}
