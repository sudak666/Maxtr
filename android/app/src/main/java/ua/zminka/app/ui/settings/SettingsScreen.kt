package ua.zminka.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import ua.zminka.app.data.repository.AuthRepository
import ua.zminka.app.ui.profile.ProfileViewModel

/**
 * MVP scope: account info + sign-out, plus the profile switcher (see
 * ProfileSection below). The web app's Settings tab is by far its
 * largest (wallets/categories/budgets/tags/auto-rules/recurring/rates/
 * widgets/PIN/premium managers — see CLAUDE.md's "UI conventions worth
 * following" section) — none of those managers are ported yet.
 * `onSignedOut` lets the caller drive navigation back to the auth
 * screen, since this screen has no nav controller of its own.
 */
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    authRepo: AuthRepository = AuthRepository(),
    profileViewModel: ProfileViewModel = viewModel(),
) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: "—"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Акаунт", style = MaterialTheme.typography.titleLarge)
        Text(email, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

        ProfileSection(viewModel = profileViewModel)

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

/**
 * Mirrors Settings → Профілі (see CLAUDE.md's "Multiple profiles per
 * account" section): a row of switchable data-profiles for the same
 * Firebase Auth account, each with its own finances/shifts/debts.
 * Switching is instant (no reload/navigation) since every repository's
 * `observe*()` flow re-subscribes automatically on
 * `ProfileManager.activeProfileId` changes.
 */
@Composable
private fun ProfileSection(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Профілі", style = MaterialTheme.typography.titleLarge)
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.displayList, key = { it.id }) { profile ->
                FilterChip(
                    selected = profile.id == state.activeProfileId,
                    onClick = { viewModel.switchProfile(profile.id) },
                    label = { Text(profile.name.ifBlank { "Профіль" }) },
                )
            }
            item {
                FilterChip(selected = false, onClick = { showAddDialog = true }, label = { Text("+ Додати") })
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name -> viewModel.addProfile(name); showAddDialog = false },
        )
    }
}

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий профіль") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }) { Text("Додати") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
