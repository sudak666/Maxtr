package ua.rytm.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonobankSyncState(
    val running: Boolean = false,
    val profileKey: String? = null,
    val progress: MonobankSyncProgress? = null,
    val result: Pair<MonobankConnection, Int>? = null,
    val error: Throwable? = null,
)

// A Monobank sync is paced at >=61s per request (Monobank's own rate limit),
// so a multi-account sync takes minutes. Running it in the sheet's own
// rememberCoroutineScope meant any recreation (rotation, theme/language
// change) or leaving the sheet cancelled it half-way. This runs it in the
// application scope instead; the sheet only observes [state], so it can be
// closed and reopened while the sync keeps going.
class MonobankSyncCoordinator(
    private val repository: MonobankRepository,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MonobankSyncState())
    val state: StateFlow<MonobankSyncState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(uid: String, profileId: String, connection: MonobankConnection) {
        if (job?.isActive == true) return
        val key = "$uid/$profileId"
        _state.value = MonobankSyncState(running = true, profileKey = key)
        job = appScope.launch {
            val outcome = runCatching {
                repository.sync(uid, profileId, connection) { p -> _state.update { it.copy(progress = p) } }
            }
            _state.value = MonobankSyncState(
                running = false,
                profileKey = key,
                result = outcome.getOrNull(),
                error = outcome.exceptionOrNull(),
            )
        }
    }

    /** Clears a finished result/error once the UI has shown it. */
    fun consume() {
        if (!_state.value.running) _state.value = MonobankSyncState()
    }
}
