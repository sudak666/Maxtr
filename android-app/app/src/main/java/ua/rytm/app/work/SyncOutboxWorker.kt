package ua.rytm.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.rytm.app.RytmApplication
import java.util.concurrent.TimeUnit

private const val UNIQUE_SYNC_OUTBOX = "sync-outbox"

class SyncOutboxWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RytmApplication
        return runCatching {
            val transactionsDrained = app.transactionsSyncRepository.drainOutbox()
            val shoppingDrained = app.shoppingSyncRepository.drainOutbox()
            transactionsDrained && shoppingDrained
        }.fold(
            onSuccess = { drained -> if (drained) Result.success() else Result.retry() },
            onFailure = { if (runAttemptCount < 8) Result.retry() else Result.failure() },
        )
    }
}

fun scheduleSyncOutbox(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_SYNC_OUTBOX, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}
