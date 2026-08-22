package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.FINANCE_WIDGET_KEYS
import ua.rytm.app.data.local.FinanceWidgetsConfig
import ua.rytm.app.data.local.SettingsStore

class WidgetSettingsSyncRepository(
    private val settingsStore: SettingsStore,
    private val firestore: FirebaseFirestore,
) {
    private val saveMutex = Mutex()
    private fun ref(uid: String, profileId: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncOnSignIn(uid: String, profileId: String) {
        val snapshot = ref(uid, profileId).get().await()
        val widgets = snapshot.get("widgets") as? Map<*, *>
        if (widgets != null) {
            val enabled = FINANCE_WIDGET_KEYS.filter { widgets[it] != false }.toSet()
            val remoteOrder = (snapshot.get("widgetOrder") as? List<*>)?.filterIsInstance<String>().orEmpty()
            settingsStore.replaceFinanceWidgets(FinanceWidgetsConfig(enabled, (remoteOrder + FINANCE_WIDGET_KEYS).distinct()))
        } else {
            save(uid, profileId)
        }
    }

    suspend fun save(uid: String, profileId: String) = saveMutex.withLock {
        val config = settingsStore.getFinanceWidgets()
        val widgets = mapOf(
            "rates" to true,
            "converter" to true,
            "analytics" to true,
            "chart" to true,
            "goals" to ("goals" in config.enabled),
            "dailyTip" to ("dailyTip" in config.enabled),
            "cryptoTop" to ("cryptoTop" in config.enabled),
        )
        ref(uid, profileId).set(
            mapOf("widgets" to widgets, "widgetOrder" to config.order, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }
}
