package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.data.local.RytmMigrations
import java.io.File

class RytmMigrationsTest {
    // @Database isn't runtime-retained; Room's exported schema (written from the
    // annotation at build time) is the source of truth for the current version.
    private val schemaDir = File("schemas/ua.rytm.app.data.local.RytmDatabase")
    private val dbVersion = schemaDir.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()

    @Test fun migrationChainIsContiguousUpToCurrentVersion() {
        var v = RytmMigrations.FIRST_MIGRATED_VERSION
        RytmMigrations.ALL.forEach {
            assertEquals("gap before ${it.startVersion}", v, it.startVersion)
            assertEquals(it.startVersion + 1, it.endVersion)
            v = it.endVersion
        }
        assertEquals("DB version bumped without a Migration", dbVersion, v)
    }

    @Test fun currentSchemaIsExported() {
        val text = File(schemaDir, "$dbVersion.json").readText()
        assertTrue(text.contains("\"version\": $dbVersion"))
        assertTrue(!text.contains("shopping_items"))
    }
}
