package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R

// Every real screen (§1.2-1.6 of ANDROID_MIGRATION.md) replaces one of these
// call sites in a dedicated follow-up step — kept here only so the NavHost
// skeleton is runnable end to end.
@Composable
internal fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.placeholder_screen, title),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
