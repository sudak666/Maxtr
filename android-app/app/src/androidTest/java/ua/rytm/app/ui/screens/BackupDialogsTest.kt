package ua.rytm.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.data.BackupPreview
import ua.rytm.app.ui.theme.RytmTheme

class BackupDialogsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewRequiresExplicitReplacementAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var confirmed = false
        compose.setContent {
            RytmTheme {
                BackupRestorePreviewDialog(
                    preview = BackupPreview(rowCount = 12, nonEmptyTableCount = 3),
                    busy = false,
                    onDismiss = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_backup_restore_preview_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_backup_restore_preview_body, 12, 3)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_backup_restore_action)).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
