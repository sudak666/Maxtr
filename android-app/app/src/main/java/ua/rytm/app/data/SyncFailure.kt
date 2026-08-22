package ua.rytm.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

data class SyncFailure(val kind: Kind, val retryable: Boolean, val diagnosticCode: String) {
    enum class Kind { NETWORK, AUTH, PERMISSION, RATE_LIMITED, CONFLICT, DATA, UNKNOWN }

    companion object {
        fun from(error: Throwable): SyncFailure {
            val causes = generateSequence(error as Throwable?) { it.cause }.take(8).toList()
            val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            val firestoreCode = firestore?.code?.name
            return when {
                firestoreCode == "PERMISSION_DENIED" -> of(Kind.PERMISSION, false)
                firestoreCode == "UNAUTHENTICATED" -> of(Kind.AUTH, false)
                firestoreCode == "RESOURCE_EXHAUSTED" -> of(Kind.RATE_LIMITED, true)
                firestoreCode == "ABORTED" -> of(Kind.CONFLICT, true)
                firestoreCode == "UNAVAILABLE" -> of(Kind.NETWORK, true)
                causes.any { it is FirebaseNetworkException || it is IOException } -> of(Kind.NETWORK, true)
                causes.any { it is FirebaseAuthException } -> of(Kind.AUTH, false)
                causes.any { it is SecurityException } -> of(Kind.PERMISSION, false)
                causes.any { it is IllegalArgumentException } -> of(Kind.DATA, false)
                else -> of(Kind.UNKNOWN, true)
            }
        }

        private fun of(kind: Kind, retryable: Boolean) =
            SyncFailure(kind, retryable, "SYNC_${kind.name}")
    }
}
