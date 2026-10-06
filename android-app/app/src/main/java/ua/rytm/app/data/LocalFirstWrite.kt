package ua.rytm.app.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex

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

/**
 * Serializes "edit Room + snapshot it to Firestore" against realtime syncs
 * that overwrite Room from the server. Without it a realtime sync triggered
 * by the previous edit's server ack could land between the next edit's Room
 * write and its snapshot: Room got the stale server state back and the
 * snapshot then pushed that stale state (seen live: toggling a shift off in
 * brush mode right after toggling it on left it on).
 */
object LocalWriteLock {
    val mutex = Mutex()
}

/**
 * Writes exactly these top-level fields, each REPLACED as a whole, and leaves
 * every other field of the doc alone. `set(map, SetOptions.merge())` deep-
 * merges nested maps instead, so a key removed locally (a cleared shift day,
 * a deleted budget/subcategory/icon) was never removed in the cloud and came
 * back on the next sync.
 */
internal fun com.google.firebase.firestore.DocumentReference.setFields(fields: Map<String, Any?>) =
    set(fields, com.google.firebase.firestore.SetOptions.mergeFields(fields.keys.toList()))
