package ua.zminka.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import ua.zminka.app.data.profile.ProfileManager

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Mirrors index.html's lock-screen: sign-in gates the app, then hands
    // off to the tab UI once Firebase Auth reports a user.
    LaunchedEffect(state.loading) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!state.loading && uid != null) {
            // Loads this uid's per-device saved profile choice (see
            // ProfileManager's own doc comment) before any repository
            // gets a chance to build a doc path off the stale default.
            ProfileManager.load(uid)
            onAuthenticated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Zminka",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        state.error?.let { err ->
            Spacer(Modifier.size(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.size(20.dp))
        Button(
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(if (state.mode == AuthMode.LOGIN) "Увійти" else "Зареєструватися")
            }
        }

        TextButton(
            onClick = {
                viewModel.setMode(if (state.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (state.mode == AuthMode.LOGIN) "Немає акаунта? Зареєструватися"
                else "Вже є акаунт? Увійти",
            )
        }
    }
}
