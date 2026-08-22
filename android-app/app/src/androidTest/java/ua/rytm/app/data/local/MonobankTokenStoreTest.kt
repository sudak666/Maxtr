package ua.rytm.app.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MonobankTokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = MonobankTokenStore(context)

    @After
    fun cleanUp() {
        store.delete(OWNER, PROFILE)
    }

    @Test
    fun tokenRoundTripsEncryptedAndDisconnectDeletesIt() {
        store.write(OWNER, PROFILE, TOKEN)

        assertEquals(TOKEN, store.read(OWNER, PROFILE))
        val persistedValues = context.getSharedPreferences("rytm_monobank_secrets", android.content.Context.MODE_PRIVATE).all.values
        assertFalse(persistedValues.any { it.toString().contains(TOKEN) })

        store.delete(OWNER, PROFILE)
        assertNull(store.read(OWNER, PROFILE))
    }

    @Test
    fun profilesAndAccountsAreCryptographicallySeparated() {
        store.write(OWNER, PROFILE, TOKEN)
        assertNull(store.read("another-owner", PROFILE))
        assertNull(store.read(OWNER, "another-profile"))
    }

    private companion object {
        const val OWNER = "owner"
        const val PROFILE = "profile"
        const val TOKEN = "secret-personal-token"
    }
}
