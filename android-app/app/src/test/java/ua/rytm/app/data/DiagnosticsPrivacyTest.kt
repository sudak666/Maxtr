package ua.rytm.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPrivacyTest {
    @Test fun diagnosticLineContainsOnlyClosedDomainAndStableCode() {
        val failure = SyncFailure(SyncFailure.Kind.PERMISSION, false, "SYNC_PERMISSION")
        assertEquals("DEBT:SYNC_PERMISSION", SafeDiagnostics.syncLine(SafeDiagnostics.Domain.DEBT, failure))
        assertEquals(
            "DEBT:SYNC_UNKNOWN",
            SafeDiagnostics.syncLine(SafeDiagnostics.Domain.DEBT, failure.copy(diagnosticCode = "user@example.com / private path")),
        )
    }

    @Test fun productionSourcesCannotLogRawExceptionContent() {
        val sourceRoot = findSourceRoot()
        val forbidden = listOf("." + "message", "localized" + "Message", "print" + "StackTrace", "record" + "Exception")
        val violations = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            forbidden.filter { token -> file.readText().contains(token) }.map { token -> "${file.name}:$token" }.asSequence()
        }.toList()
        assertTrue("Raw diagnostic data paths found: $violations", violations.isEmpty())
    }

    private fun findSourceRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val candidate = File(current, "app/src/main/java")
            if (candidate.isDirectory) return candidate
            current = current.parentFile
        }
        error("Cannot find app/src/main/java")
    }
}
