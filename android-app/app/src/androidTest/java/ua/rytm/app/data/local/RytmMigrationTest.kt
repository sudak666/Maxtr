package ua.rytm.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RytmMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RytmDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate13To17PreservesDataAndCreatesNewSchema() {
        helper.createDatabase(DB_NAME, 13).apply {
            execSQL("INSERT INTO wallets (id, name, colorHex, currency, icon) VALUES ('wallet', 'Card', 1, 'UAH', 'card')")
            execSQL("INSERT INTO transactions (id, type, amount, currency, date, walletId, targetWalletId, targetAmount, targetCurrency, category, subcategory, comment, tags, createdAt) VALUES ('tx', 'EXPENSE', 42.5, 'UAH', '2026-08-22', 'wallet', NULL, NULL, NULL, 'Food', NULL, 'kept', '', 1)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 17, true, *RytmMigrations.ALL).use { db ->
            db.query("SELECT amount, comment, monobankId, ownerUid, profileId FROM transactions WHERE id = 'tx'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(42.5, cursor.getDouble(0), 0.0)
                assertEquals("kept", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals("", cursor.getString(3))
                assertEquals("default", cursor.getString(4))
            }
            db.query("SELECT COUNT(*) FROM auto_rules").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
