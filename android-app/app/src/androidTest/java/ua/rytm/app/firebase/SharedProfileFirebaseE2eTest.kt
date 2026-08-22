package ua.rytm.app.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedProfileFirebaseE2eTest {
    private val apps = mutableListOf<FirebaseApp>()
    private lateinit var owner: Client
    private lateinit var member: Client

    @Before
    fun setUp() = runBlocking {
        owner = newClient("owner")
        member = newClient("member")
    }

    @After
    fun tearDown() {
        apps.forEach(FirebaseApp::delete)
        apps.clear()
    }

    @Test
    fun realtimeRolesReconnectAndConcurrentEditsWorkAcrossTwoAccounts() = runBlocking {
        val profileId = "android_${UUID.randomUUID().toString().replace("-", "")}"
        val members = owner.db.document("users/${owner.uid}/max_tracker/shared_members@$profileId")
        val finance = owner.db.document("users/${owner.uid}/max_tracker/finance@$profileId")
        members.set(mapOf("members" to listOf(owner.uid, member.uid), "roles" to mapOf(member.uid to "viewer"), "updatedAt" to now())).await()
        finance.set(mapOf("wallets" to emptyList<Any>(), "revision" to 0L, "updatedAt" to now())).await()

        val memberFinance = member.db.document(finance.path)
        assertEquals(0L, memberFinance.get(Source.SERVER).await().getLong("revision"))
        val denied = runCatching { memberFinance.set(mapOf("revision" to 1L, "updatedAt" to now()), SetOptions.merge()).await() }.exceptionOrNull()
        assertTrue(denied is FirebaseFirestoreException && denied.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)

        members.update("roles.${member.uid}", "editor", "updatedAt", now()).await()
        val revisions = LinkedBlockingQueue<Long>()
        val registration = memberFinance.addSnapshotListener { snapshot, error ->
            if (error == null) snapshot?.getLong("revision")?.let(revisions::offer)
        }
        try {
            memberFinance.set(mapOf("revision" to 1L, "updatedAt" to now()), SetOptions.merge()).await()
            assertTrue(revisions.awaitValue(1L))

            member.db.disableNetwork().await()
            finance.set(mapOf("revision" to 2L, "updatedAt" to now()), SetOptions.merge()).await()
            member.db.enableNetwork().await()
            assertTrue(revisions.awaitValue(2L))

            val ownerIncrement = async { owner.db.runTransaction { tx -> val value = tx.get(finance).getLong("revision") ?: 0L; tx.update(finance, "revision", value + 1, "updatedAt", now()) }.await() }
            val memberIncrement = async { member.db.runTransaction { tx -> val value = tx.get(memberFinance).getLong("revision") ?: 0L; tx.update(memberFinance, "revision", value + 1, "updatedAt", now()) }.await() }
            ownerIncrement.await()
            memberIncrement.await()
            assertEquals(4L, finance.get(Source.SERVER).await().getLong("revision"))
            assertTrue(revisions.awaitValue(4L))
        } finally {
            registration.remove()
        }
    }

    private suspend fun newClient(label: String): Client {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = FirebaseOptions.fromResource(context) ?: error("Missing Firebase options")
        val app = FirebaseApp.initializeApp(context, options, "e2e-$label-${UUID.randomUUID()}")
        apps += app
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        val db = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 8080) }
        val uid = auth.signInAnonymously().await().user?.uid ?: error("Anonymous emulator sign-in failed")
        return Client(uid, db)
    }

    private fun LinkedBlockingQueue<Long>.awaitValue(expected: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (poll(250, TimeUnit.MILLISECONDS) == expected) return true
        }
        return false
    }

    private fun now() = System.currentTimeMillis()

    private data class Client(val uid: String, val db: FirebaseFirestore)
}
