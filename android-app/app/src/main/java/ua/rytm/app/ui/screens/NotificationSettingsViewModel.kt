package ua.rytm.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ua.rytm.app.data.PushRepository

// Mirrors js/notifications.js's 4 independent toggles (toggleReminders()/
// toggleBudgetAlerts()/toggleRecurringAlerts()/toggleDebtAlerts()) plus the
// reminder-time <select> pair (populateNotifTimeSelects()/
// updateNotifTimeFromSelects()) — the granular settings step 27's own doc
// comment disclosed as a separate follow-up (see PushRepository/
// NotificationSettingsSheet). No Room table backs this domain (unlike every
// other manager sheet in this app) — notifSettings only has meaning as a
// device-independent, account-level Firestore field the server sweep reads
// directly, so a one-shot load + optimistic-local-write-through pattern is
// used instead of the Room+Flow pipeline every synced domain otherwise gets.
class NotificationSettingsViewModel(private val uid: String, private val repository: PushRepository) : ViewModel() {

    companion object {
        fun factory(uid: String, repository: PushRepository) = viewModelFactory {
            initializer { NotificationSettingsViewModel(uid, repository) }
        }
    }

    var loading by mutableStateOf(true)
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
        viewModelScope.launch {
            val s = repository.getNotifSettings(uid)
            val (h, m) = s.time.split(":").let { it.getOrElse(0) { "21" } to it.getOrElse(1) { "00" } }
            dailyReminderEnabled = s.enabled
            reminderHour = h
            reminderMinute = m
            budgetAlerts = s.budgetAlerts
            recurringAlerts = s.recurringAlerts
            debtAlerts = s.debtAlerts
            loading = false
        }
    }

    private fun currentTime() = "$reminderHour:$reminderMinute"

    // Named onXChanged, not setX — a plain setX name would collide with the
    // Kotlin-compiler-synthesized property setter for the same-named `var`
    // above (identical JVM signature, a real "platform declaration clash"
    // compile error caught immediately, not a style preference).
    fun onDailyReminderChanged(enabled: Boolean) {
        dailyReminderEnabled = enabled
        viewModelScope.launch { repository.setDailyReminder(uid, enabled, currentTime()) }
    }

    fun onReminderTimeChanged(hour: String, minute: String) {
        reminderHour = hour
        reminderMinute = minute
        // Only writes if the reminder is actually on — matches
        // js/notifications.js's updateNotifTimeFromSelects(), which is only
        // ever reachable while the reminder checkbox is checked (the time
        // <select>s are inside the same conditionally-shown block).
        if (dailyReminderEnabled) viewModelScope.launch { repository.setDailyReminder(uid, true, "$hour:$minute") }
    }

    fun onBudgetAlertsChanged(enabled: Boolean) {
        budgetAlerts = enabled
        viewModelScope.launch { repository.setBudgetAlerts(uid, enabled) }
    }

    fun onRecurringAlertsChanged(enabled: Boolean) {
        recurringAlerts = enabled
        viewModelScope.launch { repository.setRecurringAlerts(uid, enabled) }
    }

    fun onDebtAlertsChanged(enabled: Boolean) {
        debtAlerts = enabled
        viewModelScope.launch { repository.setDebtAlerts(uid, enabled) }
    }
}
