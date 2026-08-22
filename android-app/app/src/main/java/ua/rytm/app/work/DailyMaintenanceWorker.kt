package ua.rytm.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.rytm.app.RytmApplication
import com.google.firebase.auth.FirebaseAuth
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.adoptLegacyScope
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val UNIQUE_WORK_NAME = "daily-local-maintenance"

internal fun localMaintenanceDate(
    instant: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate = instant.atZone(zoneId).toLocalDate()

class DailyMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RytmApplication
        val accountUid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val profileId = app.activeProfileStore.getActiveProfileId(accountUid)
        val ownerUid = app.activeProfileStore.getActiveProfileOwnerUid(accountUid) ?: accountUid
        RoomProfileScope.activate(ownerUid, profileId)
        app.database.adoptLegacyScope(ownerUid, profileId)
        val today = localMaintenanceDate()
        return runCatching {
            app.financeRepository.processRecurring(today)
            app.shiftsRepository.processAutoFillShifts(today)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 5) Result.retry() else Result.failure() },
        )
    }
}

fun scheduleDailyMaintenance(context: Context) {
    val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(24, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
