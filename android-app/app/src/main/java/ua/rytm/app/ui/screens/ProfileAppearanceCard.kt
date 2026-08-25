package ua.rytm.app.ui.screens
import androidx.core.graphics.scale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.rytm.app.data.ProfileAppearance
import ua.rytm.app.data.ProfileAppearanceRepository
import java.io.ByteArrayOutputStream
import ua.rytm.app.ui.theme.AvatarTintBlue
import ua.rytm.app.ui.theme.AvatarTintGray
import ua.rytm.app.ui.theme.AvatarTintGreen
import ua.rytm.app.ui.theme.AvatarTintPurple
import ua.rytm.app.ui.theme.AvatarTintSky
import ua.rytm.app.ui.theme.AvatarTintPink
import ua.rytm.app.ui.theme.PurpleLight2
import ua.rytm.app.ui.theme.Slate
import ua.rytm.app.ui.theme.GreenLight2
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.BlueLight2
import ua.rytm.app.ui.theme.PinkDeep
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Bolt
import ua.rytm.app.ui.icons.DarkMode
import ua.rytm.app.ui.icons.Diamond
import ua.rytm.app.ui.icons.Edit
import ua.rytm.app.ui.icons.Favorite
import ua.rytm.app.ui.icons.LightMode
import ua.rytm.app.ui.icons.Person

private data class BuiltinAvatar(val id: String, val icon: ImageVector, val colors: List<Color>)

private val builtinAvatars = listOf(
    BuiltinAvatar("fox", RytmIcons.LightMode, listOf(AvatarTintBlue, PurpleLight2)),
    BuiltinAvatar("panda", RytmIcons.DarkMode, listOf(AvatarTintGray, Slate)),
    BuiltinAvatar("robot", RytmIcons.Bolt, listOf(AvatarTintGreen, GreenLight2)),
    BuiltinAvatar("rocket", RytmIcons.Person, listOf(AvatarTintPurple, PurpleDark)),
    BuiltinAvatar("gem", RytmIcons.Diamond, listOf(AvatarTintSky, BlueLight2)),
    BuiltinAvatar("lion", RytmIcons.Favorite, listOf(AvatarTintPink, PinkDeep)),
)

@Composable
fun ProfileAppearanceCard(
    uid: String,
    dataOwnerUid: String,
    profileId: String,
    email: String,
    repository: ProfileAppearanceRepository,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appearance by remember(uid, dataOwnerUid, profileId) { mutableStateOf(ProfileAppearance()) }
    var draft by remember(uid, dataOwnerUid, profileId) { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var avatarDialog by rememberSaveable { mutableStateOf(false) }
    val loadFailed = stringResource(R.string.profile_load_failed)
    val saveFailed = stringResource(R.string.profile_save_failed)
    val invalidImage = stringResource(R.string.profile_invalid_image)
    val avatarUpdated = stringResource(R.string.profile_avatar_updated)
    val nicknameSaved = stringResource(R.string.profile_nickname_saved)
    val avatarDescription = stringResource(R.string.profile_avatar)
    val presetDescription = stringResource(R.string.profile_avatar_preset)

    LaunchedEffect(uid, dataOwnerUid, profileId) {
        runCatching { repository.load(dataOwnerUid, profileId) }
            .onSuccess { appearance = it; draft = it.nickname }
            .onFailure { onMessage(loadFailed) }
    }

    fun save(next: ProfileAppearance, success: String) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { repository.save(dataOwnerUid, profileId, next) }
                .onSuccess { appearance = next; draft = next.nickname; onMessage(success) }
                .onFailure { onMessage(saveFailed) }
            busy = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: error("decode")
                    val side = minOf(source.width, source.height)
                    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
                    val scaled = cropped.scale(160, 160)
                    ByteArrayOutputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }.getOrNull()
            }
            if (encoded == null) {
                busy = false
                onMessage(invalidImage)
            } else {
                busy = false
                save(appearance.copy(avatar = encoded), avatarUpdated)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(appearance, draft.ifBlank { email }, avatarDescription, Modifier.clip(CircleShape).clickable(enabled = !busy) { avatarDialog = true })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.profile_nickname)) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(
                            onClick = { draft = appearance.nickname; editing = false },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            onClick = { editing = false; save(appearance.copy(nickname = draft.trim()), nicknameSaved) },
                            enabled = !busy && draft.trim() != appearance.nickname,
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appearance.nickname.ifBlank { stringResource(R.string.profile_nickname) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editing = true }, enabled = !busy) {
                            Icon(RytmIcons.Edit, stringResource(R.string.profile_edit_nickname))
                        }
                    }
                }
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (avatarDialog) {
        AlertDialog(
            onDismissRequest = { avatarDialog = false },
            title = { Text(stringResource(R.string.profile_choose_avatar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        builtinAvatars.take(3).forEach { avatar -> AvatarOption(avatar, presetDescription) { avatarDialog = false; save(appearance.copy(avatar = "builtin:${avatar.id}"), avatarUpdated) } }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        builtinAvatars.drop(3).forEach { avatar -> AvatarOption(avatar, presetDescription) { avatarDialog = false; save(appearance.copy(avatar = "builtin:${avatar.id}"), avatarUpdated) } }
                    }
                    Button(onClick = { avatarDialog = false; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_upload_photo)) }
                }
            },
            confirmButton = { TextButton(onClick = { avatarDialog = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

@Composable
private fun ProfileAvatar(appearance: ProfileAppearance, fallback: String, description: String, modifier: Modifier) {
    val builtin = builtinAvatars.firstOrNull { appearance.avatar == "builtin:${it.id}" }
    val bytes = remember(appearance.avatar) {
        appearance.avatar.takeIf { it.startsWith("data:image/") }?.substringAfter("base64,")?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
    }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        modifier = modifier.size(64.dp).clip(CircleShape).background(
            builtin?.let { Brush.linearGradient(it.colors) } ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)),
        ).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, contentDescription = description, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            builtin != null -> Icon(builtin.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            else -> Text(fallback.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AvatarOption(avatar: BuiltinAvatar, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(RytmRadii.Input)).background(Brush.linearGradient(avatar.colors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(avatar.icon, contentDescription = description, tint = Color.White) }
}
