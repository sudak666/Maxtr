package ua.rytm.app.ui.screens.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.local.clearAllProfileScopedTables

// Google Sign-In is the primary path here too (matches the PWA's
// `.auth-google.btn-primary` convention — see CLAUDE.md's Auth section),
// but the native flow is fundamentally different from js/auth.js's
// signInWithPopup()/signInWithRedirect() dance: no popup-vs-redirect
// fallback, no WebAPK redirect-loses-state gotcha (all real bugs specific
// to the browser round-trip) — Credential Manager hands back a Google ID
// token directly, in-process, which is exchanged for a Firebase credential.
// Email+password fallback (the PWA's #auth-email-section) is NOT ported in
// this step — chesno not done, see ANDROID_MIGRATION.md's step-13 section.
//
// serverClientId is the project's auto-generated "Default Web Client ID"
// (client_type:3 entry in google-services.json) — required so Firebase can
// verify the ID token's audience; it is NOT the Android OAuth client.
private const val WEB_CLIENT_ID = "311094677098-bqfj1f4nkd5ntnbd4267kb26t9rnvi28.apps.googleusercontent.com"

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var currentUser by mutableStateOf<FirebaseUser?>(auth.currentUser)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    var isSigningIn by mutableStateOf(false)
        private set

    var authMode by mutableStateOf(AuthMode.LOGIN)
        private set
    var formMessage by mutableStateOf<String?>(null)
        private set

    private val authStateListener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
    }

    // Shared by signInWithGoogle() and reauthenticateWithGoogle() (needed
    // before deleteAccount() on a session Firebase considers stale — see
    // that function's own doc comment) — both just want a fresh Google
    // AuthCredential via Credential Manager, they differ only in what they
    // do with it afterward.
    private suspend fun fetchGoogleCredential(context: Context): AuthCredential? {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
    }

    fun signInWithGoogle(context: Context) {
        if (isSigningIn) return
        isSigningIn = true
        viewModelScope.launch {
            try {
                val credential = fetchGoogleCredential(context)
                if (credential != null) {
                    auth.signInWithCredential(credential).await()
                } else {
                    publishFormMessage("Помилка входу. Спробуйте ще раз.")
                }
            } catch (e: GetCredentialException) {
                publishFormMessage("Вхід через Google скасовано або недоступний.")
            } catch (e: GoogleIdTokenParsingException) {
                publishFormMessage("Помилка обробки облікового запису Google.")
            } catch (e: Exception) {
                publishFormMessage("Помилка входу. Спробуйте ще раз.")
            } finally {
                isSigningIn = false
            }
        }
    }

    fun onAuthModeChanged(mode: AuthMode) {
        authMode = mode
        formMessage = null
    }

    fun submitEmail(emailInput: String, password: String) {
        if (isSigningIn) return
        val email = emailInput.trim()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            publishFormMessage("Некоректний email.")
            return
        }
        if (password.length < 6) {
            publishFormMessage("Пароль надто простий (мінімум 6 символів).")
            return
        }
        isSigningIn = true
        formMessage = null
        viewModelScope.launch {
            try {
                if (authMode == AuthMode.LOGIN) auth.signInWithEmailAndPassword(email, password).await()
                else auth.createUserWithEmailAndPassword(email, password).await()
            } catch (_: FirebaseNetworkException) {
                publishFormMessage("Немає з'єднання з мережею. Перевір інтернет і спробуй ще раз.")
            } catch (e: FirebaseAuthException) {
                publishFormMessage(authErrorMessage(e.errorCode))
            } catch (_: Exception) {
                publishFormMessage("Помилка входу. Спробуйте ще раз.")
            } finally {
                isSigningIn = false
            }
        }
    }

    fun resetPassword(emailInput: String) {
        if (isSigningIn) return
        val email = emailInput.trim()
        if (email.isBlank()) {
            publishFormMessage("Введіть email, щоб скинути пароль.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            publishFormMessage("Некоректний email.")
            return
        }
        isSigningIn = true
        formMessage = null
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                publishFormMessage("Лист для скидання пароля надіслано.")
            } catch (_: FirebaseNetworkException) {
                publishFormMessage("Немає з'єднання з мережею. Перевір інтернет і спробуй ще раз.")
            } catch (e: FirebaseAuthException) {
                publishFormMessage(authErrorMessage(e.errorCode))
            } catch (_: Exception) {
                publishFormMessage("Помилка входу. Спробуйте ще раз.")
            } finally {
                isSigningIn = false
            }
        }
    }

    private fun publishFormMessage(message: String) {
        formMessage = message
    }

    private fun authErrorMessage(code: String): String = when (code) {
        "ERROR_INVALID_EMAIL" -> "Некоректний email."
        "ERROR_USER_NOT_FOUND" -> "Користувача не знайдено."
        "ERROR_WRONG_PASSWORD" -> "Невірний пароль."
        "ERROR_INVALID_CREDENTIAL" -> "Невірний email або пароль."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Цей email вже зареєстровано."
        "ERROR_WEAK_PASSWORD" -> "Пароль надто простий (мінімум 6 символів)."
        "ERROR_TOO_MANY_REQUESTS" -> "Забагато спроб. Спробуйте пізніше."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Немає з'єднання з мережею. Перевір інтернет і спробуй ще раз."
        else -> "Помилка входу. Спробуйте ще раз."
    }

    fun signOut() {
        authMode = AuthMode.LOGIN
        formMessage = null
        auth.signOut()
    }

    var isDeletingAccount by mutableStateOf(false)
        private set

    // Mirrors js/auth.js's deleteAccountUser(): delete this account's own
    // (default-profile) Firestore data, then the Firebase Auth account
    // itself, then wipe local caches. Same disclosed scope simplification
    // as the PWA — a secondary @profileId-suffixed doc, or shared_members/
    // profile_invites references, are left behind as harmless orphans under
    // a uid nobody can ever sign back into (see CLAUDE.md's Multiple
    // profiles / Shared profiles sections for why the PWA itself doesn't
    // clean those up either).
    fun deleteAccount(context: Context) {
        val user = auth.currentUser ?: return
        if (isDeletingAccount) return
        isDeletingAccount = true
        val uid = user.uid
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            try {
                val profileCol = db.collection("users").document(uid).collection("max_tracker")
                val txSnap = profileCol.document("finance").collection("transactions").get().await()
                if (txSnap.documents.isNotEmpty()) {
                    val batch = db.batch()
                    txSnap.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                listOf("shifts", "finance", "debt").forEach { profileCol.document(it).delete().await() }
            } catch (e: Exception) {
                errorMessage = "Не вдалося видалити дані"
                isDeletingAccount = false
                return@launch
            }
            try {
                user.delete().await()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                // Firebase requires a session newer than some threshold for
                // account deletion specifically — the PWA hits the same
                // auth/requires-recent-login error and re-authenticates
                // in place before retrying, same shape here.
                val credential = try { fetchGoogleCredential(context) } catch (e2: Exception) { null }
                if (credential == null) {
                    errorMessage = "Потрібен повторний вхід через Google для видалення акаунту"
                    isDeletingAccount = false
                    return@launch
                }
                try {
                    user.reauthenticate(credential).await()
                    user.delete().await()
                } catch (e2: Exception) {
                    errorMessage = "Не вдалося видалити акаунт"
                    isDeletingAccount = false
                    return@launch
                }
            } catch (e: Exception) {
                errorMessage = "Не вдалося видалити акаунт"
                isDeletingAccount = false
                return@launch
            }
            val app = context.applicationContext as RytmApplication
            app.pinStore.removePin(uid)
            app.database.clearAllProfileScopedTables()
            isDeletingAccount = false
        }
    }

    // Only ever wired up behind BuildConfig.USE_FIREBASE_EMULATOR (see
    // build.gradle.kts/RytmApplication) — lets a session verify Firestore sync
    // code end to end against the local emulator without a live Google account or
    // typing any real credentials into anything.
    fun signInAnonymouslyForTesting() {
        if (isSigningIn) return
        isSigningIn = true
        viewModelScope.launch {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                errorMessage = "Emulator anonymous sign-in failed: ${e.message}"
            } finally {
                isSigningIn = false
            }
        }
    }
}

enum class AuthMode { LOGIN, REGISTER }
