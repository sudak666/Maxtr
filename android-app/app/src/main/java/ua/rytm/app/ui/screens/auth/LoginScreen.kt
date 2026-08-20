package ua.rytm.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.BuildConfig

// Google Sign-In only — mirrors the PWA's primary CTA
// (`.auth-google.btn-primary`), but the email+password fallback
// (`#auth-email-section`) is NOT ported in this step. Chesno not done: see
// ANDROID_MIGRATION.md's step-13 section for why (scoped deliberately, the
// same "smallest real, verified increment" discipline every step here has
// followed).
@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Rytm", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        Text(
            "Графік змін, фінанси та розрахунки — все в одному місці",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )

        Button(
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !viewModel.isSigningIn,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (viewModel.isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Увійти через Google", fontWeight = FontWeight.SemiBold)
            }
        }

        // Test-only, never shown outside a -PuseFirebaseEmulator=true build — see
        // AuthViewModel.signInAnonymouslyForTesting()'s doc comment.
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            TextButton(
                onClick = viewModel::signInAnonymouslyForTesting,
                enabled = !viewModel.isSigningIn,
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("[emulator] Анонімний вхід для тестів") }
        }
    }

    viewModel.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("Не вдалося увійти") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text("Гаразд") } },
        )
    }
}
