package ua.rytm.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCommentHygieneTest {
    @Test fun productionCommentsDoNotContainMigrationHistory() {
        val history = Regex("ANDROID_MIGRATION|real bug found|chesno|migration step|step[- ]\\d+", RegexOption.IGNORE_CASE)
        val violations = findSourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    line.takeIf { it.trimStart().startsWith("//") && history.containsMatchIn(it) }
                        ?.let { "${file.name}:${index + 1}" }
                }
            }
            .toList()
        assertTrue("Migration history belongs in project docs: $violations", violations.isEmpty())
    }

    @Test fun settingsScreenDoesNotRegrowIntoSectionImplementations() {
        val screen = File(findSourceRoot(), "ua/rytm/app/ui/screens/SettingsScreen.kt")
        assertTrue("SettingsScreen must remain an orchestrator", screen.readLines().size <= 850)
    }

    @Test fun financeViewModelKeepsPureCalculationsExtracted() {
        val viewModel = File(findSourceRoot(), "ua/rytm/app/ui/screens/finance/FinanceViewModel.kt")
        assertTrue("FinanceViewModel must remain orchestration-focused", viewModel.readLines().size <= 420)
    }

    @Test fun financeScreenKeepsTransactionRowsExtracted() {
        val finance = File(findSourceRoot(), "ua/rytm/app/ui/screens/finance")
        assertTrue("FinanceScreen must remain screen-level composition", File(finance, "FinanceScreen.kt").readLines().size <= 350)
        assertTrue("Finance components must remain presentation-only", File(finance, "FinanceScreenComponents.kt").readLines().size <= 450)
        assertTrue("TransactionRow must remain a focused component", File(finance, "TransactionRow.kt").readLines().size <= 200)
    }

    private fun findSourceRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            File(current, "app/src/main/java").takeIf(File::isDirectory)?.let { return it }
            current = current.parentFile
        }
        error("Cannot find app/src/main/java")
    }
}
