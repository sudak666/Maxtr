package ua.rytm.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RytmMigrations
import java.io.File

/**
 * Runs the real migration chain on databases built from the exported schemas
 * (app/schemas/, served as debug assets) and lets Room validate the result
 * against the current entities — a wrong ALTER/CREATE fails here instead of
 * crashing an updated phone on start. 15 is the oldest version that reached
 * Play (vc3). AUDIT_2026-10 P1-01.
 */
@Config(sdk = [35], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class RytmMigrationPathTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RytmDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // @Database isn't runtime-retained; the newest exported schema is the current version.
    private val current = File("schemas/ua.rytm.app.data.local.RytmDatabase").listFiles()!!
        .mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()

    @Test fun from15KeepsUserData() = migrateKeepingData(15)
    @Test fun from16KeepsUserData() = migrateKeepingData(16)
    @Test fun from17KeepsUserData() = migrateKeepingData(17)

    private fun migrateKeepingData(start: Int) {
        // Absolute path: with a bare name Room 2.8's helper rejects the path it resolves itself.
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-$start.db").absolutePath
        helper.createDatabase(name, start).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, currency, date, walletId, category, comment, tags, createdAt) " +
                    "VALUES ('t1', 'EXPENSE', 319.2, 'UAH', '2026-01-31', 'w1', 'Продукти', 'хліб', '', 1)",
            )
            db.execSQL(
                "INSERT INTO recurring (id, type, amount, category, walletId, frequency, nextDate, active, comment) " +
                    "VALUES ('r1', 'EXPENSE', 9000.0, 'Оренда', 'w1', 'monthly', '2026-01-31', 1, '')",
            )
        }
        helper.runMigrationsAndValidate(name, current, true, *RytmMigrations.ALL).use { db ->
            db.query("SELECT amount, comment FROM transactions WHERE id = 't1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(319.2, c.getDouble(0), 0.0)
                assertEquals("хліб", c.getString(1))
            }
            db.query("SELECT nextDate, anchorDay FROM recurring WHERE id = 'r1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2026-01-31", c.getString(0))
                // Added in v18: old rows have none and fall back to nextDate's day.
                assertTrue(c.isNull(1))
            }
        }
    }
}
