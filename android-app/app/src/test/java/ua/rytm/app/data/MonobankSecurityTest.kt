package ua.rytm.app.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MonobankSecurityTest {
    @Test
    fun remoteRepresentationNeverContainsToken() {
        val connection = MonobankConnection(
            token = "secret-personal-token",
            clientName = "Test",
            accounts = listOf(MonobankAccount("account", "account", "Black", "UAH")),
            mapping = mapOf("account" to "wallet"),
            lastSyncAt = 123L,
        )

        val remote = connection.toRemoteMap()

        assertFalse("token" in remote)
        assertEquals("Test", remote["clientName"])
    }
}
