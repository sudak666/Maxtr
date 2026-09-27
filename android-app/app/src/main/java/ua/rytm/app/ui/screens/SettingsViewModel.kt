package ua.rytm.app.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.CsvImportPreview
import ua.rytm.app.data.TransactionsCsvRepository

/** A snackbar message, resolved by the screen so it follows the in-app language. */
sealed interface SettingsMessage {
    data class Text(@StringRes val res: Int, val arg: Any? = null) : SettingsMessage
    data class Plural(@PluralsRes val res: Int, val count: Int) : SettingsMessage
}

// CSV import/export and push registration used to run in SettingsScreen's
// own rememberCoroutineScope: switching tabs or recreating the Activity
// mid-operation cancelled it half-way, and the rememberSaveable busy flag
// then restored as `true` with no job left to clear it, leaving the CSV
// buttons disabled. viewModelScope outlives both.
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RytmApplication
    private val csvRepository by lazy { TransactionsCsvRepository(app.database, FirebaseFirestore.getInstance()) }

    var message by mutableStateOf<SettingsMessage?>(null)
        private set
    fun consumeMessage() { message = null }
    fun postMessage(m: SettingsMessage) { message = m }

    var csvBusy by mutableStateOf(false)
        private set
    var csvImportPreview by mutableStateOf<CsvImportPreview?>(null)
        private set
    fun dismissImportPreview() { if (!csvBusy) csvImportPreview = null }

    var pushBusy by mutableStateOf(false)
        private set
    var pendingPushEnabled by mutableStateOf<Boolean?>(null)
        private set
    fun settlePendingPush(actual: Boolean) { if (pendingPushEnabled == actual) pendingPushEnabled = null }

    fun exportCsv(uri: Uri, language: String) {
        if (csvBusy) return
        csvBusy = true
        viewModelScope.launch {
            try {
                val csv = csvRepository.export(language)
                val written = withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)); true } ?: false
                }
                message = SettingsMessage.Text(if (written) R.string.settings_csv_exported else R.string.settings_csv_export_failed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = SettingsMessage.Text(R.string.settings_csv_export_failed)
            } finally {
                csvBusy = false
            }
        }
    }

    fun readImport(uri: Uri) {
        if (csvBusy) return
        csvBusy = true
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
                if (text == null) {
                    message = SettingsMessage.Text(R.string.settings_csv_read_failed)
                    return@launch
                }
                val preview = csvRepository.parse(text)
                when {
                    preview.transactions.isNotEmpty() -> csvImportPreview = preview
                    preview.errors.isEmpty() -> message = SettingsMessage.Text(R.string.settings_csv_empty)
                    else -> message = SettingsMessage.Plural(R.plurals.settings_csv_no_valid_rows, preview.errors.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = SettingsMessage.Text(R.string.settings_csv_import_failed)
            } finally {
                csvBusy = false
            }
        }
    }

    fun confirmImport(dataOwnerUid: String, profileId: String) {
        val preview = csvImportPreview ?: return
        if (csvBusy) return
        csvBusy = true
        viewModelScope.launch {
            try {
                csvRepository.import(dataOwnerUid, profileId, preview.transactions)
                message = SettingsMessage.Text(R.string.settings_csv_imported, preview.transactions.size)
                csvImportPreview = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = SettingsMessage.Text(R.string.settings_csv_import_save_failed)
            } finally {
                csvBusy = false
            }
        }
    }

    // Mirrors js/notifications.js's enablePushNotifications(): persist the
    // user's intent first, then register/unregister the FCM token.
    fun setPushEnabled(accountUid: String, dataOwnerUid: String, profileId: String, target: Boolean) {
        if (pushBusy) return
        pendingPushEnabled = target
        pushBusy = true
        viewModelScope.launch {
            var preferenceSaved = false
            try {
                app.settingsStore.setPushEnabled(accountUid, target)
                preferenceSaved = true
                withTimeout(10_000) {
                    if (target) app.pushRepository.enable(accountUid, dataOwnerUid, profileId)
                    else app.pushRepository.disable(accountUid, dataOwnerUid, profileId)
                }
                message = SettingsMessage.Text(if (target) R.string.settings_push_enabled else R.string.settings_push_disabled)
            } catch (e: TimeoutCancellationException) {
                // withTimeout's own exception IS a CancellationException — must be
                // handled as a failure here, not rethrown as a scope cancellation.
                Log.e("RytmPush", "Push registration timed out; keeping user preference=$target", e)
                message = SettingsMessage.Text(R.string.settings_push_change_failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RytmPush", "Push registration failed; keeping user preference=$target", e)
                message = SettingsMessage.Text(R.string.settings_push_change_failed)
                if (!preferenceSaved) pendingPushEnabled = null
            } finally {
                pushBusy = false
            }
        }
    }

    // Deletes remote docs, then local tables — a multi-step destructive
    // operation that must never be cancelled half-way by leaving the screen.
    var resetProfileBusy by mutableStateOf(false)
        private set
    var resetProfileSucceeded by mutableStateOf(false)
        private set
    fun consumeResetSucceeded() { resetProfileSucceeded = false }

    fun resetProfile(uid: String, profileId: String, activeProfileOwnerUid: String?) {
        if (resetProfileBusy) return
        resetProfileBusy = true
        viewModelScope.launch {
            try {
                app.profileSyncCoordinator.resetOwnProfile(uid, profileId, activeProfileOwnerUid)
                resetProfileSucceeded = true
                message = SettingsMessage.Text(R.string.settings_reset_success)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = SettingsMessage.Text(R.string.settings_reset_failed)
            } finally {
                resetProfileBusy = false
            }
        }
    }
}
