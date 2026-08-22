package ua.rytm.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.rytm.app.BuildConfig
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.local.MonobankTokenStore
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.clearActiveProfileTables
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MonobankFirebaseE2eTest {
    private val app = ApplicationProvider.getApplicationContext<RytmApplication>()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val tokenStore = MonobankTokenStore(app)
    private lateinit var uid: String
    private val profileId = "mono_e2e"

    @Before fun setUp() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATOR) { "Run with -PuseFirebaseEmulator=true" }
        uid = auth.signInAnonymously().await().user!!.uid
        RoomProfileScope.activate(uid, profileId)
        app.database.clearActiveProfileTables()
        tokenStore.delete(uid, profileId)
    }

    @After fun tearDown() = runBlocking {
        tokenStore.delete(uid, profileId)
        app.database.clearActiveProfileTables()
        auth.currentUser?.delete()?.await()
        auth.signOut()
    }

    @Test fun migratesLegacyTokenThenReconnectsSyncsAndDisconnectsWithoutRemoteSecret() = runBlocking {
        financeRef().set(
            mapOf(
                "integrations" to mapOf(
                    "monobank" to mapOf(
                        "token" to "legacy-secret",
                        "clientName" to "Legacy",
                        "accounts" to emptyList<Any>(),
                        "mapping" to emptyMap<String, String>(),
                    ),
                ),
            ),
        ).await()
        val repository = repositoryWithFakeApi()

        assertEquals("legacy-secret", repository.load(uid, profileId)?.token)
        assertEquals("legacy-secret", tokenStore.read(uid, profileId))
        assertNull(financeRef().get(Source.SERVER).await().get("integrations.monobank.token"))

        repository.disconnect(uid, profileId)
        assertNull(tokenStore.read(uid, profileId))
        assertNull(financeRef().get(Source.SERVER).await().get("integrations.monobank"))

        val connected = repository.connect(uid, profileId, " new-device-secret ")
        assertEquals("new-device-secret", tokenStore.read(uid, profileId))
        assertEquals(1, connected.accounts.size)
        assertNull(financeRef().get(Source.SERVER).await().get("integrations.monobank.token"))
        assertFalse(financeRef().get(Source.SERVER).await().data.toString().contains("new-device-secret"))

        val (updated, imported) = repository.sync(uid, profileId, connected) {}
        assertEquals(1, imported)
        assertTrue(updated.lastSyncAt != null)
        assertEquals(1, app.database.transactionDao().getAllOnce().size)
        assertEquals(1, financeRef().collection("transactions").get(Source.SERVER).await().size())

        repository.disconnect(uid, profileId)
        assertNull(tokenStore.read(uid, profileId))
        assertNull(repository.load(uid, profileId))
    }

    private fun repositoryWithFakeApi() = MonobankRepository(
        app.database,
        firestore,
        auth,
        tokenStore,
        requestOverride = { action, _, token ->
            assertEquals("new-device-secret", token)
            when (action) {
                "client-info" -> JSONObject(
                    """{"name":"Test Client","accounts":[{"id":"account-1","type":"black","currencyCode":980,"maskedPan":["**** 1234"]}],"jars":[]}""",
                )
                "statement" -> JSONArray(
                    """[{"id":"statement-1","time":${Instant.now().epochSecond},"amount":-12345,"description":"Test purchase","hold":false}]""",
                )
                else -> error("Unexpected action: $action")
            }
        },
        requestGapMs = 0,
    )

    private fun financeRef() = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))
}
