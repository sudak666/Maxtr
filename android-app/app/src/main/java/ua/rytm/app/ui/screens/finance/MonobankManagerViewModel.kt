package ua.rytm.app.ui.screens.finance

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.data.MonobankConnection
import ua.rytm.app.data.MonobankHttpException
import ua.rytm.app.data.MonobankNoAccountsException
import ua.rytm.app.data.MonobankOwnerOnlyException
import ua.rytm.app.data.MonobankRepository
import ua.rytm.app.data.suspendRunCatching

/**
 * Connect/disconnect/load for the Monobank sheet. They used to run in the
 * sheet's own coroutine scope, so closing the sheet or rotating mid-way
 * cancelled an awaited Firestore write half done (same reason SettingsViewModel
 * owns CSV import and push toggles).
 */
class MonobankManagerViewModel(
    private val repository: MonobankRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {
    companion object {
        fun factory(repository: MonobankRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { MonobankManagerViewModel(repository, uid, profileId) }
        }

        @StringRes
        fun errorText(throwable: Throwable): Int = when (throwable) {
            is MonobankHttpException -> when (throwable.status) {
                401, 403 -> R.string.monobank_invalid_token
                429 -> R.string.monobank_rate_limit
                else -> R.string.monobank_connection_failed
            }
            is MonobankNoAccountsException -> R.string.monobank_no_accounts
            is MonobankOwnerOnlyException -> R.string.monobank_owner_only
            else -> R.string.monobank_connection_failed
        }
    }

    var connection by mutableStateOf<MonobankConnection?>(null)
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    @get:StringRes
    var errorRes by mutableStateOf<Int?>(null)
    @get:StringRes
    var messageRes by mutableStateOf<Int?>(null)

    init {
        viewModelScope.launch {
            suspendRunCatching { repository.load(uid, profileId) }
                .onSuccess { connection = it }
                .onFailure { errorRes = errorText(it) }
            loading = false
        }
    }

    fun connect(token: String, onConnected: () -> Unit) {
        if (busy) return
        busy = true; errorRes = null
        viewModelScope.launch {
            suspendRunCatching { repository.connect(uid, profileId, token) }
                .onSuccess { connection = it; messageRes = R.string.monobank_connected; onConnected() }
                .onFailure { errorRes = errorText(it) }
            busy = false
        }
    }

    fun disconnect() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            suspendRunCatching { repository.disconnect(uid, profileId) }
                .onSuccess { connection = null; messageRes = R.string.monobank_disconnected }
                .onFailure { errorRes = errorText(it) }
            busy = false
        }
    }
}
