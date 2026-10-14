package ua.rytm.app.summary

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import ua.rytm.app.MainActivity
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.push.NOTIFICATION_CHANNEL_ID
import ua.rytm.app.push.ensureNotificationChannel
import ua.rytm.app.ui.screens.finance.MonthSummary
import ua.rytm.app.ui.screens.finance.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * On the 1st–3rd of a month, once: a local "your September in money" notification
 * opening the recap sheet. Local on purpose — a server push would also reach the
 * PWA, which has no recap screen. Follows the in-app push toggle as consent and
 * leaves the sums out when "hide amounts" is on.
 */
class MonthSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RytmApplication
        val today = LocalDate.now()
        if (today.dayOfMonth > 3) return Result.success()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (!app.settingsStore.isPushEnabled(uid).first()) return Result.success()
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return Result.success()
        val month = YearMonth.from(today).minusMonths(1)
        if (app.settingsStore.monthSummaryNotified(uid).first() == month.toString()) return Result.success()

        val rates = app.financeRepository.currencyRates.first()
        val summary = MonthSummary.compute(app.financeRepository.transactions.first(), month) {
            app.financeRepository.convertCurrency(it.amount, it.currency, "UAH", rates)
        }
        if (summary.isEmpty) return Result.success()

        val locale = app.resources.configuration.locales[0] ?: Locale.getDefault()
        val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
        val hidden = app.settingsStore.hideAmounts.first()
        val body = if (hidden) app.getString(R.string.month_summary_notification_hidden)
        else app.getString(R.string.month_summary_notification_body, "${formatMoney(summary.expense, locale)} ₴", "${formatMoney(summary.income, locale)} ₴")
        val open = PendingIntent.getActivity(
            app, 7, Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_MONTH_SUMMARY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ensureNotificationChannel(app)
        val notification = NotificationCompat.Builder(app, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(app, R.color.ic_launcher_background))
            .setContentTitle(app.getString(R.string.month_summary_title, monthName))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val canPost = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!canPost) return Result.success()
        try { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { return Result.success() }
        app.settingsStore.setMonthSummaryNotified(uid, month.toString())
        return Result.success()
    }

    companion object {
        private const val NOTIFICATION_ID = 4107
        private const val WORK_NAME = "month-summary"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthSummaryWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
