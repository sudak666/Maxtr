package ua.rytm.app.ui.screens

import ua.rytm.app.ui.theme.RytmSpacing
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import ua.rytm.app.R
import ua.rytm.app.ui.icons.AccountBalance
import ua.rytm.app.ui.icons.Description
import ua.rytm.app.ui.icons.PrivacyTip
import ua.rytm.app.ui.icons.Public
import ua.rytm.app.ui.icons.RytmIcons

/** One tappable Settings row described as data (the Finance group is a list of these). */
class SettingsLink(val icon: ImageVector, @StringRes val title: Int, @StringRes val subtitle: Int, val onClick: () -> Unit)

/** Bank-push suggestions row: opens the system notification-access switch after a disclosure. */
@Composable
internal fun SettingsRowScope.BankSuggestionsRow() {
    // Bank-push suggestions: system-level notification access is
    // the switch itself, so this opens that screen; the subtitle
    // shows whether it is currently granted (re-read on resume).
    val bankContext = LocalContext.current
    var bankEnabled by remember { mutableStateOf(ua.rytm.app.bank.BankNotificationListener.isEnabled(bankContext)) }
    // Suggestions are themselves notifications: without POST_NOTIFICATIONS
    // they were dropped silently (found live — the listener got real ПУМБ
    // pushes, nothing appeared). Ask for it on the way to the system switch.
    fun openListenerSettings() {
        runCatching { bankContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
    val bankNotifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { openListenerSettings() }
    val canPostNotifications = androidx.core.app.NotificationManagerCompat.from(bankContext).areNotificationsEnabled()
    // Prominent disclosure (Play User Data policy): say exactly what is
    // read and what is not before sending the user to the system switch.
    var bankDisclosureOpen by remember { mutableStateOf(false) }
    if (bankDisclosureOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bankDisclosureOpen = false },
            title = { Text(stringResource(R.string.bank_disclosure_title)) },
            text = { Text(stringResource(R.string.bank_disclosure_body)) },
            confirmButton = {
                ua.rytm.app.ui.components.RytmTextButton(onClick = {
                    bankDisclosureOpen = false
                    if (!canPostNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bankNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else openListenerSettings()
                }) { Text(stringResource(R.string.bank_disclosure_ok)) }
            },
            dismissButton = {
                ua.rytm.app.ui.components.RytmTextButton(onClick = { bankDisclosureOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        bankEnabled = ua.rytm.app.bank.BankNotificationListener.isEnabled(bankContext)
        onPauseOrDispose { }
    }
    SettingsRow(
        icon = RytmIcons.AccountBalance,
        badgeColor = SettingsGroupColors.Notifications,
        title = stringResource(R.string.bank_listener_title),
        subtitle = stringResource(
            when {
                bankEnabled && !canPostNotifications -> R.string.bank_listener_blocked
                bankEnabled -> R.string.bank_listener_on
                else -> R.string.bank_listener_off
            },
        ),
        onClick = {
            if (bankEnabled && !canPostNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bankNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else bankDisclosureOpen = true
        },
    )
}

@Composable
fun AboutSection(onOpenUrl: (String) -> Unit) {
    SettingsSectionLabel(stringResource(R.string.settings_about))
    SettingsGroupCard {
        SettingsRow(
            icon = RytmIcons.Public,
            badgeColor = SettingsGroupColors.About,
            title = stringResource(R.string.settings_web),
            subtitle = stringResource(R.string.settings_web_subtitle),
            onClick = { onOpenUrl("https://maxtr-c238f.web.app") },
        )
        // privacy.html/terms.html have no i18n of their own (standalone
        // static pages, not part of the app's string-resource system) —
        // legal_docs_en picks the -en.html sibling instead of silently
        // always opening the Ukrainian original regardless of app language.
        val legalDocsEn = androidx.compose.ui.res.booleanResource(R.bool.legal_docs_en)
        SettingsRow(
            icon = RytmIcons.Description,
            badgeColor = SettingsGroupColors.About,
            title = stringResource(R.string.settings_terms_row),
            subtitle = stringResource(R.string.settings_terms_subtitle),
            onClick = { onOpenUrl(if (legalDocsEn) "https://maxtr-c238f.web.app/terms-en.html" else "https://maxtr-c238f.web.app/terms.html") },
        )
        SettingsRow(
            icon = RytmIcons.PrivacyTip,
            badgeColor = SettingsGroupColors.About,
            title = stringResource(R.string.settings_privacy_row),
            subtitle = stringResource(R.string.settings_privacy_subtitle),
            onClick = { onOpenUrl(if (legalDocsEn) "https://maxtr-c238f.web.app/privacy-en.html" else "https://maxtr-c238f.web.app/privacy.html") },
        )
    }
    Text(
        stringResource(R.string.settings_about_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = RytmSpacing.sm, bottom = RytmSpacing.xs),
    )
}
