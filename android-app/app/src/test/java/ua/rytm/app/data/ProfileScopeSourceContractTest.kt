package ua.rytm.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileScopeSourceContractTest {
    @Test fun remoteSyncNeverUsesImplicitActiveRoomScope() {
        val dataDir = findDataDir()
        val files = dataDir.listFiles().orEmpty().filter { it.name.endsWith("SyncRepository.kt") || it.name == "MonobankRepository.kt" }
        val forbidden = Regex("\\b(getAllOnce|getOnce|getAllMonobankIds|clearAll)\\(\\s*\\)")
        val violations = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.name}:${index + 1}" else null
            }
        }
        assertTrue("Implicit Room profile scope in remote sync: $violations", violations.isEmpty())
    }

    private fun findDataDir(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val candidate = File(current, "app/src/main/java/ua/rytm/app/data")
            if (candidate.isDirectory) return candidate
            current = current.parentFile
        }
        error("Cannot find app data source directory")
    }
}
