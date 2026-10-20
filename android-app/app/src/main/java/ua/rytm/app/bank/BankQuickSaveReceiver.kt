package ua.rytm.app.bank

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.toEntity
import ua.rytm.app.push.NOTIFICATION_CHANNEL_ID
import ua.rytm.app.ui.builtInTextResource
import ua.rytm.app.ui.screens.finance.FinanceInsights
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.currencySymbol
import ua.rytm.app.ui.screens.finance.formatMoney
import java.time.LocalDate

/**
 * The category buttons on a bank suggestion («Кафе · Продукти · Інше»):
 * one tap saves the entry without opening the app, then the notification turns
 * into «Додано … · Скасувати». A tap is the user's explicit choice — nothing is
 * ever saved without one. Falls back to opening the pre-filled form when the
 * entry can't be saved here (signed out, read-only shared profile, no wallet).
 */
class BankQuickSaveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as RytmApplication
        scope.launch {
            try {
                withTimeout(9_000) {
                    when (intent.action) {
                        ACTION_SAVE -> save(context, app, intent)
                        ACTION_UNDO -> undo(context, app, intent)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                openForm(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun profilePath(app: RytmApplication): Pair<String, String>? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val profileId = app.activeProfileStore.getActiveProfileId(uid)
        val owner = app.activeProfileStore.getActiveProfileOwnerUid(uid)
        if (!app.profilesRepository.canEditProfile(uid, owner, profileId)) return null
        return (owner ?: uid) to profileId
    }

    private suspend fun save(context: Context, app: RytmApplication, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val prefill = BankNotificationListener.prefillFrom(intent) ?: return
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: return
        val path = profilePath(app) ?: return openForm(context, intent)
        val wallet = WalletMatcher.pick(app.financeRepository.wallets.first(), prefill.account, prefill.bank, prefill.currency)
            ?: return openForm(context, intent)
        val me = FirebaseAuth.getInstance().currentUser
        val tx = Transaction(
            id = java.util.UUID.randomUUID().toString(),
            type = if (prefill.isIncome) TxType.INCOME else TxType.EXPENSE,
            amount = prefill.amount, currency = wallet.currency, date = LocalDate.now().toString(),
            walletId = wallet.id, category = category, comment = prefill.comment?.takeIf { it.isNotBlank() },
            createdBy = me?.uid, createdByName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore('@'),
        )
        app.transactionsSyncRepository.saveTransaction(path.first, path.second, tx.toEntity())
        app.financeRepository.upsertTransaction(tx)
        notifySaved(context, notifId, tx, wallet.name)
    }

    private suspend fun undo(context: Context, app: RytmApplication, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val txId = intent.getStringExtra(EXTRA_TX_ID) ?: return
        val path = profilePath(app) ?: return
        app.transactionsSyncRepository.deleteTransaction(path.first, path.second, txId)
        app.financeRepository.deleteTransaction(txId)
        NotificationManagerCompat.from(context).cancel(TAG, notifId)
    }

    /**
     * A receiver may not start an activity (Android 12+ blocks notification
     * trampolines), so the fallback is the same suggestion again, now saying it
     * could not be saved here — its tap opens the pre-filled form.
     */
    private fun openForm(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val open = PendingIntent.getActivity(context, notifId * 4, BankNotificationListener.formIntent(context, intent), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rytm)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(context.getString(R.string.bank_quick_failed))
            .setContentText(context.getString(R.string.bank_suggest_tap))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(TAG, notifId, n) } catch (_: SecurityException) { }
    }

    private fun notifySaved(context: Context, notifId: Int, tx: Transaction, walletName: String) {
        val undo = Intent(context, BankQuickSaveReceiver::class.java).apply {
            action = ACTION_UNDO
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            putExtra(EXTRA_TX_ID, tx.id)
        }
        val undoPending = PendingIntent.getBroadcast(context, notifId * 4 + 3, undo, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = builtInTextResource(tx.category)?.let(context::getString) ?: tx.category
        val n = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rytm)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(context.getString(R.string.bank_quick_saved, "${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}", label))
            .setContentText(listOfNotNull(tx.comment, walletName).joinToString(" · "))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(UNDO_WINDOW_MS)
            .addAction(0, context.getString(R.string.action_undo), undoPending)
            .build()
        try { NotificationManagerCompat.from(context).notify(TAG, notifId, n) } catch (_: SecurityException) { }
    }

    companion object {
        const val TAG = "bank"
        const val ACTION_SAVE = "ua.rytm.app.bank.QUICK_SAVE"
        const val ACTION_UNDO = "ua.rytm.app.bank.QUICK_UNDO"
        const val EXTRA_CATEGORY = "ua.rytm.app.bank.CATEGORY"
        const val EXTRA_NOTIFICATION_ID = "ua.rytm.app.bank.NOTIFICATION_ID"
        const val EXTRA_TX_ID = "ua.rytm.app.bank.TX_ID"
        private const val UNDO_WINDOW_MS = 10 * 60 * 1000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Up to three one-tap categories for a suggestion: the type's most used
         * ones over 90 days, topped up from its category list. Empty → no buttons.
         */
        fun quickCategories(transactions: List<Transaction>, categories: List<String>, isIncome: Boolean, today: LocalDate): List<String> {
            val type = if (isIncome) TxType.INCOME else TxType.EXPENSE
            val frequent = FinanceInsights.frequentCategories(transactions, type, categories.toSet(), today.minusDays(90), limit = 3)
            return (frequent + categories).distinct().take(3)
        }
    }
}
