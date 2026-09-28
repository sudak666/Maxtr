package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** One server-side snapshot (metadata only — payload chunks are not client-readable). */
data class BackupInfo(
    val id: String,
    val profileId: String,
    val createdAt: Long,
    val reason: String,
    val txCount: Int,
    val bytes: Long,
)

sealed interface BackupCreateResult {
    data object Created : BackupCreateResult
    data object Unchanged : BackupCreateResult
    data object Empty : BackupCreateResult
}

class BackupException(val code: FirebaseFunctionsException.Code?, cause: Throwable) : Exception(cause)

/**
 * Cloud backups (see CLAUDE.md "Cloud backups" / functions/lib/backup.js).
 * Reads metadata straight from Firestore; every write goes through the
 * `backups` callable, since the rules make backups Admin-SDK-only.
 */
class BackupsRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    /** Live list of [profileId]'s backups, newest first. */
    fun observe(uid: String, profileId: String): Flow<List<BackupInfo>> = callbackFlow {
        val registration = firestore.collection("users").document(uid).collection("backups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents.orEmpty().mapNotNull { d ->
                    val pid = d.getString("profileId") ?: return@mapNotNull null
                    if (pid != profileId) return@mapNotNull null
                    BackupInfo(
                        id = d.id,
                        profileId = pid,
                        createdAt = d.getLong("createdAt") ?: return@mapNotNull null,
                        reason = d.getString("reason") ?: "daily",
                        txCount = (d.getLong("txCount") ?: 0L).toInt(),
                        bytes = d.getLong("bytes") ?: 0L,
                    )
                }.sortedByDescending { it.createdAt }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun create(profileId: String, reason: String = "manual"): BackupCreateResult {
        val result = call(mapOf("action" to "create", "profileId" to profileId, "reason" to reason))
        return when (result?.get("status")) {
            "unchanged" -> BackupCreateResult.Unchanged
            "empty" -> BackupCreateResult.Empty
            else -> BackupCreateResult.Created
        }
    }

    suspend fun restore(backupId: String) {
        call(mapOf("action" to "restore", "backupId" to backupId))
    }

    private suspend fun call(data: Map<String, Any>): Map<*, *>? = try {
        functions.getHttpsCallable("backups").apply { setTimeout(180, TimeUnit.SECONDS) }.call(data).await().getData() as? Map<*, *>
    } catch (e: FirebaseFunctionsException) {
        throw BackupException(e.code, e)
    }
}
