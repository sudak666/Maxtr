package ua.rytm.app.bank

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ua.rytm.app.MainActivity
import ua.rytm.app.R
import ua.rytm.app.push.NOTIFICATION_CHANNEL_ID
import ua.rytm.app.push.ensureNotificationChannel
import ua.rytm.app.ui.screens.finance.formatMoney

/** Form pre-fill handed from a bank-push suggestion to the new-transaction sheet. */
data class TxPrefill(val isIncome: Boolean, val amount: Double, val currency: String, val comment: String?, val isTransfer: Boolean = false)

/**
 * Reads ONLY the notifications of the bank apps in
 * [BankNotificationParser.BANK_PACKAGES] (every other package returns at the
 * first line, nothing is stored or sent anywhere) and turns a recognised
 * payment into a Rytm notification "Додати витрату 245 ₴ · Сільпо?". Tapping
 * it opens the pre-filled form; nothing is ever saved without the user.
 * Active only while the user has granted notification access in system
 * settings (Settings → Сповіщення → «Операції з банківських сповіщень»).
 */
class BankNotificationListener : NotificationListenerService() {
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in BankNotificationParser.BANK_PACKAGES) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        // Banks re-post/update the same notification; suggest each payment once.
        val dedupeKey = "${sbn.packageName}|$title|$text"
        if (!seen.add(dedupeKey)) return
        if (seen.size > 50) seen.remove(seen.first())
        val suggestion = BankNotificationParser.parse(sbn.packageName, title, text) ?: return
        val now = System.currentTimeMillis()
        recent.removeAll { now - it.at > PAIR_WINDOW_MS }
        // A move between the user's own accounts arrives as a debit and a credit
        // of the same amount within seconds (seen live: ПУМБ *5536 → *3924).
        // Offer one transfer instead of an expense plus an income.
        val pair = recent.firstOrNull {
            it.pkg == sbn.packageName && it.s.isIncome != suggestion.isIncome &&
                it.s.currency == suggestion.currency && kotlin.math.abs(it.s.amount - suggestion.amount) < 0.005
        }
        if (pair != null) {
            recent.remove(pair)
            NotificationManagerCompat.from(this).cancel("bank", pair.id)
            suggest(this, suggestion.copy(merchant = null), pair.id, transfer = true)
            return
        }
        val id = dedupeKey.hashCode()
        recent += Recent(sbn.packageName, suggestion, now, id)
        suggest(this, suggestion, id)
    }

    private data class Recent(val pkg: String, val s: BankSuggestion, val at: Long, val id: Int)
    private val recent = mutableListOf<Recent>()

    companion object {
        const val EXTRA_PREFILL_INCOME = "ua.rytm.app.PREFILL_INCOME"
        const val EXTRA_PREFILL_AMOUNT = "ua.rytm.app.PREFILL_AMOUNT"
        const val EXTRA_PREFILL_CURRENCY = "ua.rytm.app.PREFILL_CURRENCY"
        const val EXTRA_PREFILL_COMMENT = "ua.rytm.app.PREFILL_COMMENT"
        const val EXTRA_PREFILL_TRANSFER = "ua.rytm.app.PREFILL_TRANSFER"
        private const val PAIR_WINDOW_MS = 3 * 60 * 1000L

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun prefillFrom(intent: Intent?): TxPrefill? {
            val amount = intent?.getDoubleExtra(EXTRA_PREFILL_AMOUNT, -1.0)?.takeIf { it > 0 } ?: return null
            return TxPrefill(
                isIncome = intent.getBooleanExtra(EXTRA_PREFILL_INCOME, false),
                amount = amount,
                currency = intent.getStringExtra(EXTRA_PREFILL_CURRENCY) ?: "UAH",
                comment = intent.getStringExtra(EXTRA_PREFILL_COMMENT),
                isTransfer = intent.getBooleanExtra(EXTRA_PREFILL_TRANSFER, false),
            )
        }

        private fun suggest(context: Context, s: BankSuggestion, id: Int, transfer: Boolean = false) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            ensureNotificationChannel(context)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_NEW_TRANSACTION)
                putExtra(EXTRA_PREFILL_INCOME, s.isIncome)
                putExtra(EXTRA_PREFILL_AMOUNT, s.amount)
                putExtra(EXTRA_PREFILL_CURRENCY, s.currency)
                putExtra(EXTRA_PREFILL_COMMENT, s.merchant)
                putExtra(EXTRA_PREFILL_TRANSFER, transfer)
            }
            val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val symbol = when (s.currency) { "USD" -> "$"; "EUR" -> "€"; else -> "₴" }
            val title = context.getString(
                when { transfer -> R.string.bank_suggest_transfer; s.isIncome -> R.string.bank_suggest_income; else -> R.string.bank_suggest_expense },
                "${formatMoney(s.amount)} $symbol",
            )
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
                .setContentTitle(title)
                .setContentText(s.merchant ?: context.getString(R.string.bank_suggest_tap))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify("bank", id, notification)
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS revoked — nothing to show.
            }
        }
    }
}
