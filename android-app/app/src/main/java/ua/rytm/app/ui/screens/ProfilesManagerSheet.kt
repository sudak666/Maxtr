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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import ua.rytm.app.ui.components.RytmDestructiveConfirm
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Check
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.Edit
import ua.rytm.app.ui.icons.ExitToApp
import ua.rytm.app.ui.icons.Group
import ua.rytm.app.ui.icons.MoreVert
import ua.rytm.app.ui.icons.Share

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
    var newName by rememberSaveable { mutableStateOf("") }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var joinCode by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RytmSheetTitle(stringResource(R.string.profiles_title), subtitle = stringResource(R.string.profiles_body))

            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.action_dismiss)) }
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
                    Icon(RytmIcons.Add, contentDescription = null)
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

    // These three used to be hand-rolled AlertDialogs with plain TextButton
    // confirms (one of them, delete, even the exact same drift
    // RytmDestructiveConfirm's own doc comment describes finding and fixing
    // once already for "reset profile data"/"delete account" — this file
    // just never got migrated along with those). RytmDestructiveConfirm is
    // this app's one confirm-then-proceed dialog pattern, no exceptions —
    // used even for non-destructive-but-blocking cases like Sign Out.
    viewModel.pendingLeave?.let { profile ->
        RytmDestructiveConfirm(
            title = stringResource(R.string.profile_leave_title),
            body = stringResource(R.string.profile_leave_body, localizedDomainText(profile.name)),
            confirmLabel = stringResource(R.string.profile_leave),
            onConfirm = viewModel::confirmLeave,
            onDismiss = viewModel::cancelLeave,
        )
    }

    viewModel.pendingDeleteId?.let {
        RytmDestructiveConfirm(
            title = stringResource(R.string.profile_delete_title),
            body = stringResource(R.string.profile_delete_body),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    viewModel.pendingSwitch?.let { target ->
        RytmDestructiveConfirm(
            title = stringResource(R.string.profile_switch_title),
            body = stringResource(R.string.profile_switch_body),
            confirmLabel = stringResource(R.string.profile_switch),
            busy = viewModel.switching,
            busyLabel = stringResource(R.string.profile_switching),
            onConfirm = {
                scope.launch {
                    // onSwitched() (which the caller uses to dismiss this
                    // sheet and show a success toast) only fires on a real
                    // success — see confirmSwitch()'s own doc comment for
                    // the real bug this guards against.
                    if (viewModel.confirmSwitch()) onSwitched(target.id)
                }
            },
            onDismiss = viewModel::cancelSwitch,
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
            IconButton(onClick = { onRename(text) }) { Icon(RytmIcons.Check, contentDescription = stringResource(R.string.action_save)) }
            IconButton(onClick = onCancelRename) { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.action_cancel)) }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Chart),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isActive) 2.dp else 0.dp,
    ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // The "Перемкнути" TextButton + up to 4 IconButtons alongside
            // this column eat most of a narrow screen's width, and without
            // a maxLines cap the name wrapped letter-by-letter into a
            // squeezed vertical sliver instead of truncating (reported
            // live, screenshot: "Max" rendered as "Ma"/"x" stacked).
            Text(
                localizedDomainText(profile.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isActive) Text(stringResource(R.string.profile_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (profile.isShared) Text(stringResource(R.string.profile_shared), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (!isActive) {
            TextButton(onClick = onSwitch) { Text(stringResource(R.string.profile_switch)) }
        }
        if (profile.isShared) {
            IconButton(onClick = onLeave) { Icon(RytmIcons.ExitToApp, contentDescription = stringResource(R.string.profile_leave)) }
        } else {
            // Was 4 separate IconButtons (Share/Members/Rename/Delete)
            // inline, alongside the "Перемкнути" TextButton on non-active
            // rows -- 5 interactive elements left the name column almost no
            // width, truncating a real name down to one letter + ellipsis
            // (flagged live, screenshot: "Stas" showing as "S..."). The
            // active row has none of these and rendered fine, confirming
            // it's this crowding, not the ellipsis logic itself. Collapsed
            // into a single overflow menu, same Box+IconButton+DropdownMenu
            // pattern CategoriesManagerSheet.kt already uses for its own
            // row actions -- "Перемкнути" stays inline since it's the
            // single most common action on a non-active row.
            var menuOpen by remember(profile.id) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(RytmIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_share)) },
                        leadingIcon = { Icon(RytmIcons.Share, contentDescription = null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_members)) },
                        leadingIcon = { Icon(RytmIcons.Group, contentDescription = null) },
                        onClick = { menuOpen = false; onManageMembers() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_rename)) },
                        leadingIcon = { Icon(RytmIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onStartRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(RytmIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
    }
}
