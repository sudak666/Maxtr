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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    private val authStateListener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
    }

    fun signInWithGoogle(context: Context) {
        if (isSigningIn) return
        isSigningIn = true
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).await()
                } else {
                    errorMessage = "Не вдалося отримати обліковий запис Google"
                }
            } catch (e: GetCredentialException) {
                errorMessage = "Вхід через Google скасовано або недоступно"
            } catch (e: GoogleIdTokenParsingException) {
                errorMessage = "Помилка обробки облікового запису Google"
            } catch (e: Exception) {
                errorMessage = "Помилка входу: ${e.message}"
            } finally {
                isSigningIn = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
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
