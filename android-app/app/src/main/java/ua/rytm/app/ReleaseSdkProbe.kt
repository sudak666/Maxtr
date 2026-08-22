package ua.rytm.app

import android.content.Context
import androidx.annotation.Keep
import androidx.credentials.CredentialManager
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Side-effect-free runtime probe used by the minified release device gate. */
@Keep
internal object ReleaseSdkProbe {
    @JvmStatic
    fun run(context: Context) {
        checkNotNull(FirebaseAuth.getInstance())
        checkNotNull(FirebaseFirestore.getInstance())
        checkNotNull(FirebaseMessaging.getInstance())
        checkNotNull(FirebaseAppCheck.getInstance())
        checkNotNull(CredentialManager.create(context))
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).close()
    }
}
