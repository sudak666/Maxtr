package ua.rytm.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.rytm.app.data.ProfileAppearance
import ua.rytm.app.data.ProfileAppearanceRepository
import java.io.ByteArrayOutputStream

private data class BuiltinAvatar(val id: String, val icon: ImageVector, val colors: List<Color>)

private val builtinAvatars = listOf(
    BuiltinAvatar("fox", Icons.Filled.LightMode, listOf(Color(0xFFA8C7FA), Color(0xFF0B57D0))),
    BuiltinAvatar("panda", Icons.Filled.DarkMode, listOf(Color(0xFFC4C7C5), Color(0xFF5F6368))),
    BuiltinAvatar("robot", Icons.Filled.Bolt, listOf(Color(0xFFA8DAB5), Color(0xFF137333))),
    BuiltinAvatar("rocket", Icons.Filled.Person, listOf(Color(0xFFD7AEFB), Color(0xFF7E3FF2))),
    BuiltinAvatar("gem", Icons.Filled.Diamond, listOf(Color(0xFFAECBFA), Color(0xFF1A73E8))),
    BuiltinAvatar("lion", Icons.Filled.Favorite, listOf(Color(0xFFF8BBD0), Color(0xFFC2185B))),
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
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var avatarDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uid, dataOwnerUid, profileId) {
        runCatching { repository.load(dataOwnerUid, profileId) }
            .onSuccess { appearance = it; draft = it.nickname }
            .onFailure { onMessage("Не вдалося завантажити профіль") }
    }

    fun save(next: ProfileAppearance, success: String) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { repository.save(dataOwnerUid, profileId, next) }
                .onSuccess { appearance = next; draft = next.nickname; onMessage(success) }
                .onFailure { onMessage("Не вдалося зберегти профіль") }
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
                    val scaled = Bitmap.createScaledBitmap(cropped, 160, 160, true)
                    ByteArrayOutputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }.getOrNull()
            }
            if (encoded == null) {
                busy = false
                onMessage("Оберіть коректне зображення")
            } else {
                busy = false
                save(appearance.copy(avatar = encoded), "Аватар оновлено")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(appearance, draft.ifBlank { email }, Modifier.clickable(enabled = !busy) { avatarDialog = true })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        enabled = editing && !busy,
                        singleLine = true,
                        placeholder = { Text("Нікнейм") },
                    )
                    if (!editing) IconButton(onClick = { editing = true }, enabled = !busy) { Icon(Icons.Filled.Edit, "Редагувати нікнейм") }
                    else Button(onClick = { editing = false; save(appearance.copy(nickname = draft.trim()), "Нікнейм збережено") }, enabled = !busy && draft.trim() != appearance.nickname) { Text("Зберегти") }
                }
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (avatarDialog) {
        AlertDialog(
            onDismissRequest = { avatarDialog = false },
            title = { Text("Оберіть аватар") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        builtinAvatars.take(3).forEach { avatar -> AvatarOption(avatar) { avatarDialog = false; save(appearance.copy(avatar = "builtin:${avatar.id}"), "Аватар оновлено") } }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        builtinAvatars.drop(3).forEach { avatar -> AvatarOption(avatar) { avatarDialog = false; save(appearance.copy(avatar = "builtin:${avatar.id}"), "Аватар оновлено") } }
                    }
                    Button(onClick = { avatarDialog = false; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Завантажити фото") }
                }
            },
            confirmButton = { TextButton(onClick = { avatarDialog = false }) { Text("Готово") } },
        )
    }
}

@Composable
private fun ProfileAvatar(appearance: ProfileAppearance, fallback: String, modifier: Modifier) {
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
            bitmap != null -> Image(bitmap, contentDescription = "Аватар", modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            builtin != null -> Icon(builtin.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            else -> Text(fallback.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AvatarOption(avatar: BuiltinAvatar, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(avatar.colors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(avatar.icon, contentDescription = avatar.id, tint = Color.White) }
}
