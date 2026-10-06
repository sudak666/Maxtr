package ua.rytm.app.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Writes rejected by the server after they were queued (security rules,
 * quota). Collected once in RytmNavHost and shown as a snackbar.
 */
object SyncWriteErrors {
    private val _events = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    val events: SharedFlow<Throwable> = _events.asSharedFlow()
    internal fun report(e: Throwable) { _events.tryEmit(e) }
}

/**
 * Queues a Firestore write and returns immediately. A write Task only
 * completes once the SERVER acknowledges it, so `.await()` on it suspended
 * forever offline: the save button spun, the sheet never closed and every
 * later edit queued behind the repositories' mutexes. Firestore's own
 * offline cache already persists the write, applies it to local reads and
 * delivers it in issue order once the network is back, so nothing is lost
 * by not waiting. Never use this for flows that genuinely need the server's
 * answer (invites, account deletion, push token registration).
 */
internal fun Task<*>.enqueue() {
    addOnFailureListener { e ->
        android.util.Log.w("RytmSync", "Queued write rejected", e)
        SyncWriteErrors.report(e)
    }
}
