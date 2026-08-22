package ua.rytm.app

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckIntegrationTest {
    @Test
    fun registeredDebugProviderReturnsValidToken() = runBlocking {
        val token = FirebaseAppCheck.getInstance().getAppCheckToken(true).await().token
        assertTrue("Firebase App Check returned an empty token", token.isNotBlank())
    }
}
