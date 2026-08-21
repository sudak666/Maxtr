package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.ProfileMeta

// Mirrors js/color-picker.js's profiles-modal (renderProfilesUI()) — see
// ProfilesManagerViewModel's own doc comment for the own-profiles-only
// scope of this step. A switched-to profile takes effect live: every screen
// already observes its data through Room Flows, so once
// ProfileSyncCoordinator.switchProfile() finishes clearing+resyncing the
// local tables, the currently-open tab just re-renders with the new
// profile's data — no navigation/restart needed, same as the PWA's own
// re-render-in-place after switchProfile().
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesManagerSheet(
    uid: String,
    onDismiss: () -> Unit,
    onSwitched: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as RytmApplication
    val viewModel: ProfilesManagerViewModel = viewModel(factory = ProfilesManagerViewModel.factory(app, uid))
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Профілі", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            viewModel.errorMessage?.let { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            }

            if (viewModel.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            viewModel.profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    isActive = profile.id == viewModel.activeProfileId,
                    renaming = renamingId == profile.id,
                    onStartRename = { renamingId = profile.id },
                    onRename = { name -> viewModel.renameProfile(profile.id, name); renamingId = null },
                    onCancelRename = { renamingId = null },
                    onSwitch = { viewModel.requestSwitch(profile.id) },
                    onDelete = { viewModel.requestDelete(profile.id) },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Назва профілю") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = { viewModel.addProfile(newName); newName = "" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Додати")
                }
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Видалити профіль") },
            text = { Text("Видалити цей профіль? Дані на сервері залишаться, але доступ до них з цього застосунку буде втрачено.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Скасувати") } },
        )
    }

    viewModel.pendingSwitchId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.switching) viewModel.cancelSwitch() },
            title = { Text("Перемкнути профіль") },
            text = {
                if (viewModel.switching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Перемикання…")
                    }
                } else {
                    Text("Перемкнутися на цей профіль? Поточні дані на екрані заміняться даними вибраного профілю.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !viewModel.switching,
                    onClick = {
                        scope.launch {
                            // onSwitched() (which the caller uses to dismiss this
                            // sheet and show a success toast) only fires on a real
                            // success — see confirmSwitch()'s own doc comment for
                            // the real bug this guards against.
                            if (viewModel.confirmSwitch()) onSwitched(targetId)
                        }
                    },
                ) { Text("Перемкнути") }
            },
            dismissButton = { TextButton(enabled = !viewModel.switching, onClick = viewModel::cancelSwitch) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileMeta,
    isActive: Boolean,
    renaming: Boolean,
    onStartRename: () -> Unit,
    onRename: (String) -> Unit,
    onCancelRename: () -> Unit,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    if (renaming) {
        var text by remember(profile.id) { mutableStateOf(profile.name) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = { onRename(text) }) { Icon(Icons.Filled.Check, contentDescription = "Зберегти") }
            IconButton(onClick = onCancelRename) { Icon(Icons.Filled.Close, contentDescription = "Скасувати") }
        }
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (isActive) Text("Активний", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (!isActive) {
            TextButton(onClick = onSwitch) { Text("Перемкнути") }
        }
        IconButton(onClick = onStartRename) { Icon(Icons.Filled.Edit, contentDescription = "Перейменувати") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
    }
}
