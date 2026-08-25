package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.R
import ua.rytm.app.data.ProfileMeta
import ua.rytm.app.ui.localizedDomainText
import androidx.compose.foundation.layout.imePadding

// Mirrors js/color-picker.js's profiles-modal (renderProfilesUI()), plus
// (step 32) the "Поділитись поточним профілем"/"Приєднатися за кодом" rows
// from the same modal. A switched-to profile takes effect live: every
// screen already observes its data through Room Flows, so once
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
    var joinCode by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            if (viewModel.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            viewModel.profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    isActive = viewModel.isRowActive(profile),
                    renaming = renamingId == profile.id,
                    onStartRename = { renamingId = profile.id },
                    onRename = { name -> viewModel.renameProfile(profile.id, name); renamingId = null },
                    onCancelRename = { renamingId = null },
                    onSwitch = { viewModel.requestSwitch(profile) },
                    onDelete = { viewModel.requestDelete(profile) },
                    onShare = { viewModel.shareProfile(profile) },
                    onLeave = { viewModel.requestLeave(profile) },
                    onManageMembers = { viewModel.openMembersManager(profile) },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(enabled = newName.isNotBlank() && !viewModel.loading, onClick = { viewModel.addProfile(newName); newName = "" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.action_add))
                }
            }

            Text(stringResource(R.string.profile_join_by_code), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    label = { Text(stringResource(R.string.profile_invite_code)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(enabled = joinCode.isNotBlank() && !viewModel.joining, onClick = { viewModel.joinByCode(joinCode); joinCode = "" }) {
                    Text(stringResource(R.string.profile_join))
                }
            }
        }
    }

    viewModel.inviteCode?.let { code ->
        AlertDialog(
            onDismissRequest = viewModel::consumeInviteCode,
            title = { Text(stringResource(R.string.profile_invite_code)) },
            text = { Text(stringResource(R.string.profile_invite_body, code)) },
            confirmButton = { TextButton(onClick = viewModel::consumeInviteCode) { Text(stringResource(R.string.action_done)) } },
        )
    }

    viewModel.pendingLeave?.let { profile ->
        AlertDialog(
            onDismissRequest = viewModel::cancelLeave,
            title = { Text(stringResource(R.string.profile_leave_title)) },
            text = { Text(stringResource(R.string.profile_leave_body, localizedDomainText(profile.name))) },
            confirmButton = { TextButton(onClick = viewModel::confirmLeave) { Text(stringResource(R.string.profile_leave), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelLeave) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    viewModel.pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.switching) viewModel.cancelSwitch() },
            title = { Text(stringResource(R.string.profile_switch_title)) },
            text = {
                if (viewModel.switching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.profile_switching))
                    }
                } else {
                    Text(stringResource(R.string.profile_switch_body))
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
                            if (viewModel.confirmSwitch()) onSwitched(target.id)
                        }
                    },
                ) { Text(stringResource(R.string.profile_switch)) }
            },
            dismissButton = { TextButton(enabled = !viewModel.switching, onClick = viewModel::cancelSwitch) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    viewModel.managingMembersFor?.let { profile ->
        AlertDialog(
            onDismissRequest = viewModel::closeMembersManager,
            title = { Text(stringResource(R.string.profile_members_title, localizedDomainText(profile.name))) },
            text = {
                when {
                    viewModel.membersLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    viewModel.members == null -> Text(stringResource(R.string.profile_members_not_shared))
                    else -> {
                        val currentUid = uid
                        val others = viewModel.members!!.members.filter { it != currentUid }
                        if (others.isEmpty()) {
                            Text(stringResource(R.string.profile_members_empty))
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                others.forEach { memberUid ->
                                    val role = viewModel.members!!.roles[memberUid] ?: "editor"
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        // No cross-account display-name infra exists (this app has no
                                        // contacts/friends list) — a shortened uid is the only real
                                        // identifier available, same as js/color-picker.js's own
                                        // renderSharedMembersList().
                                        Text(memberUid.take(8) + "…", style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { viewModel.toggleMemberRole(memberUid, role) }) {
                                            Text(stringResource(if (role == "viewer") R.string.profile_role_viewer else R.string.profile_role_editor))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeMembersManager) { Text(stringResource(R.string.action_done)) } },
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
    onShare: () -> Unit,
    onLeave: () -> Unit,
    onManageMembers: () -> Unit,
) {
    if (renaming) {
        var text by remember(profile.id) { mutableStateOf(profile.name) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = { onRename(text) }) { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save)) }
            IconButton(onClick = onCancelRename) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel)) }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isActive) 2.dp else 0.dp,
    ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(localizedDomainText(profile.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isActive) Text(stringResource(R.string.profile_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (profile.isShared) Text(stringResource(R.string.profile_shared), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (!isActive) {
            TextButton(onClick = onSwitch) { Text(stringResource(R.string.profile_switch)) }
        }
        if (profile.isShared) {
            IconButton(onClick = onLeave) { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.profile_leave)) }
        } else {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.profile_share)) }
            IconButton(onClick = onManageMembers) { Icon(Icons.Filled.Group, contentDescription = stringResource(R.string.profile_members)) }
            IconButton(onClick = onStartRename) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_rename)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
    }
}
