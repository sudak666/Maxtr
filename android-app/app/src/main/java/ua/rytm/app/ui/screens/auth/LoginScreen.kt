package ua.rytm.app.ui.screens.auth
import androidx.core.net.toUri

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.BuildConfig
import ua.rytm.app.R
import ua.rytm.app.ui.theme.Purple3
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect

@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
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
                        .shadow(18.dp, RoundedCornerShape(RytmRadii.Chart), ambientColor = PurpleDark.copy(.35f), spotColor = PurpleDark.copy(.35f))
                        .clip(RoundedCornerShape(RytmRadii.Chart)).background(Brush.linearGradient(listOf(PurpleDark, Purple3))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("R", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Text("Rytm", style = MaterialTheme.typography.displaySmall, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                Text(
                    stringResource(R.string.auth_tagline),
                    fontSize = 13.5.sp,
                    lineHeight = 20.25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).padding(top = 22.dp).shadow(24.dp, RoundedCornerShape(RytmRadii.AuthCard)),
                shape = RoundedCornerShape(RytmRadii.AuthCard),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        enabled = !viewModel.isSigningIn,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(RytmRadii.Pill),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_google_g),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 10.dp).size(18.dp),
                        )
                        Text(stringResource(R.string.auth_continue_google), style = MaterialTheme.typography.labelLarge)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    AuthModeTabs(viewModel.authMode, !viewModel.isSigningIn, viewModel::onAuthModeChanged)
                    AuthFieldLabel(stringResource(R.string.auth_email))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        // heightIn, not height: a fixed 48dp box clipped the
                        // field's own text at fontScale >= 1.3 (M3's own
                        // minimum is 56dp).
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = !viewModel.isSigningIn,
                        singleLine = true,
                        placeholder = { Text("you@example.com") },
                        shape = RoundedCornerShape(RytmRadii.Control),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = authFieldColors(),
                    )

                    AuthFieldLabel(stringResource(R.string.auth_password))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = !viewModel.isSigningIn,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.auth_password_hint)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            // Autocorrect on a mobile keyboard is the single
                            // biggest cause of failed sign-ins without this.
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                                    ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(RytmRadii.Control),
                        colors = authFieldColors(),
                    )

                    Text(
                        text = viewModel.formMessageRes?.let { stringResource(it) }.orEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = submit,
                        enabled = !viewModel.isSigningIn,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(RytmRadii.Pill),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                    ) {
                        // Pressing sign-in used to look like nothing happened:
                        // the button only went `enabled = false`.
                        if (viewModel.isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(stringResource(if (viewModel.authMode == AuthMode.LOGIN) R.string.auth_sign_in else R.string.auth_register), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    }

                    TextButton(onClick = { viewModel.resetPassword(email) }, enabled = !viewModel.isSigningIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.auth_forgot_password), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    TermsFooter(
                        onTerms = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://maxtr-c238f.web.app/terms.html".toUri())) },
                        onPrivacy = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://maxtr-c238f.web.app/privacy.html".toUri())) },
                    )

                    if (BuildConfig.USE_FIREBASE_EMULATOR) {
                        TextButton(onClick = viewModel::signInAnonymouslyForTesting, enabled = !viewModel.isSigningIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(stringResource(R.string.auth_emulator_sign_in))
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbarHostState, Modifier.safeDrawingPadding())
    }

    // Sign-in failures are a notification, not a decision.
    viewModel.errorMessageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }
}

@Composable
private fun AuthModeTabs(mode: AuthMode, enabled: Boolean, onModeChanged: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Pill)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(RytmRadii.Pill)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AuthModeTab(stringResource(R.string.auth_login_tab), mode == AuthMode.LOGIN, enabled, Modifier.weight(1f)) { onModeChanged(AuthMode.LOGIN) }
        AuthModeTab(stringResource(R.string.auth_register_tab), mode == AuthMode.REGISTER, enabled, Modifier.weight(1f)) { onModeChanged(AuthMode.REGISTER) }
    }
}

@Composable
private fun AuthModeTab(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(RytmRadii.Pill),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) { Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun AuthFieldLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, letterSpacing = .78.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun TermsFooter(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val prefix = stringResource(R.string.auth_terms_prefix)
    val terms = stringResource(R.string.terms_title)
    val and = stringResource(R.string.common_and)
    val privacy = stringResource(R.string.privacy_title)
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text(
        text = buildAnnotatedString {
            append("$prefix ")
            withLink(LinkAnnotation.Clickable("terms") { onTerms() }) { withStyle(linkStyle) { append(terms) } }
            append(" $and ")
            withLink(LinkAnnotation.Clickable("privacy") { onPrivacy() }) { withStyle(linkStyle) { append(privacy) } }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
