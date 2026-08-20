plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "ua.rytm.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ua.rytm.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // Dev/test-only escape hatch to point Firebase Auth/Firestore at the local
        // emulator suite instead of production maxtr-c238f — off by default, opt in
        // with `./gradlew assembleDebug -PuseFirebaseEmulator=true`. See
        // ANDROID_MIGRATION.md's step-14/15 sections for why this exists: verifying
        // Firestore sync code needs a signed-in user, and this repo can't complete a
        // real Google sign-in (no live account) or type real credentials into
        // anything — anonymous auth against a local emulator is the honest way to
        // actually exercise this code path instead of leaving it merely compiled.
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", (project.findProperty("useFirebaseEmulator") == "true").toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.androidx.ui.tooling)
}
