package ua.zminka.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import ua.zminka.app.data.repository.AuthRepository

/**
 * MVP scope: just account info + sign-out. The web app's Settings tab is
 * by far its largest (wallets/categories/budgets/tags/auto-rules/
 * recurring/rates/widgets/PIN/premium/profiles managers — see CLAUDE.md's
 * "UI conventions worth following" section) — none of those managers are
 * ported yet. `onSignedOut` lets the caller drive navigation back to the
 * auth screen, since this screen has no nav controller of its own.
 */
@Composable
fun SettingsScreen(onSignedOut: () -> Unit, authRepo: AuthRepository = AuthRepository()) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: "—"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Акаунт", style = MaterialTheme.typography.titleLarge)
        Text(email, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

        Button(
            onClick = {
                authRepo.signOut()
                onSignedOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Вийти")
        }
    }
}
