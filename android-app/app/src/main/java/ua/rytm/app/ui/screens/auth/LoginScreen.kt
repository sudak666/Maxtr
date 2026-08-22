package ua.rytm.app.ui.screens.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.BuildConfig
import ua.rytm.app.ui.theme.Purple3
import ua.rytm.app.ui.theme.PurpleDark

@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val submit = { viewModel.submitEmail(email, password) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(PurpleDark.copy(alpha = .14f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = 560.dp.toPx(),
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(68.dp)
                        .shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = PurpleDark.copy(.35f), spotColor = PurpleDark.copy(.35f))
                        .clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(PurpleDark, Purple3))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("R", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Text("Rytm", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                Text(
                    "Фінанси, зміни та борги в одному місці",
                    fontSize = 13.5.sp,
                    lineHeight = 20.25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).padding(top = 22.dp).shadow(24.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        enabled = !viewModel.isSigningIn,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleDark, contentColor = Color.White),
                    ) {
                        Text("G", fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
                        Text("Продовжити через Google", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text("АБО", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    AuthModeTabs(viewModel.authMode, !viewModel.isSigningIn, viewModel::onAuthModeChanged)
                    AuthFieldLabel("EMAIL")
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !viewModel.isSigningIn,
                        singleLine = true,
                        placeholder = { Text("you@example.com") },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = authFieldColors(),
                    )

                    AuthFieldLabel("ПАРОЛЬ")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !viewModel.isSigningIn,
                        singleLine = true,
                        placeholder = { Text("Мінімум 6 символів") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(14.dp),
                        colors = authFieldColors(),
                    )

                    Text(
                        text = viewModel.formMessage.orEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = submit,
                        enabled = !viewModel.isSigningIn,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleDark, contentColor = Color.White),
                    ) {
                        Text(if (viewModel.authMode == AuthMode.LOGIN) "Увійти" else "Зареєструватися", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    TextButton(onClick = { viewModel.resetPassword(email) }, enabled = !viewModel.isSigningIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Забули пароль?", color = PurpleDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    TermsFooter(
                        onTerms = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maxtr-c238f.web.app/terms.html"))) },
                        onPrivacy = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maxtr-c238f.web.app/privacy.html"))) },
                    )

                    if (BuildConfig.USE_FIREBASE_EMULATOR) {
                        TextButton(onClick = viewModel::signInAnonymouslyForTesting, enabled = !viewModel.isSigningIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("[emulator] Анонімний вхід для тестів")
                        }
                    }
                }
            }
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

@Composable
private fun AuthModeTabs(mode: AuthMode, enabled: Boolean, onModeChanged: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AuthModeTab("Вхід", mode == AuthMode.LOGIN, enabled, Modifier.weight(1f)) { onModeChanged(AuthMode.LOGIN) }
        AuthModeTab("Реєстрація", mode == AuthMode.REGISTER, enabled, Modifier.weight(1f)) { onModeChanged(AuthMode.REGISTER) }
    }
}

@Composable
private fun AuthModeTab(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(999.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PurpleDark else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AuthFieldLabel(label: String) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .78.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = PurpleDark,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun TermsFooter(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Реєструючись, ти погоджуєшся з", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onTerms, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("Умовами використання", color = PurpleDark, fontSize = 13.sp) }
            Text("і", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onPrivacy, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("Політикою конфіденційності", color = PurpleDark, fontSize = 13.sp) }
        }
    }
}
