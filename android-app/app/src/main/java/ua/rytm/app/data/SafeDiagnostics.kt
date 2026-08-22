package ua.rytm.app.data

import android.util.Log

/** Privacy boundary: diagnostics accept enums/codes only, never Throwable or user data. */
object SafeDiagnostics {
    enum class Domain { PROFILE, TRANSACTIONS, SHOPPING, DEBT, SHIFTS }

    fun reportSync(domain: Domain, failure: SyncFailure) {
        Log.w(TAG, syncLine(domain, failure))
    }

    internal fun syncLine(domain: Domain, failure: SyncFailure): String {
        val code = failure.diagnosticCode.takeIf { it.matches(SAFE_CODE) } ?: "SYNC_UNKNOWN"
        return "${domain.name}:$code"
    }

    private const val TAG = "RytmDiagnostic"
    private val SAFE_CODE = Regex("^SYNC_[A-Z_]{1,40}$")
}
