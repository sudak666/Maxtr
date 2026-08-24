import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Release signing reads from a local, gitignored keystore.properties (see
// .gitignore) so the real keystore path/passwords never touch source
// control or an agent's own context — copy keystore.properties.example to
// keystore.properties and fill in the real values yourself. This is the
// SAME upload key the old TWA build (published to Play's Closed testing
// track, package ua.rytm.app, 2026-07-29) was already signed with — Play
// requires every new upload for an existing package to share that same
// upload-key lineage, so the native app can only replace it, not use a
// fresh key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "ua.rytm.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ua.rytm.app"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Bumped past the TWA build's own versionCode 1 (2026-07-29 Closed
        // testing release) — Play rejects an upload whose versionCode
        // doesn't strictly increase over the package's last one, TWA or not.
        versionCode = 2
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

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only wired up once keystore.properties exists locally (see its
            // own doc comment above) — an unsigned/local-only release build
            // still works for `bundleRelease` without it, it just won't
            // produce something Play will accept as an update to the
            // existing package.
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("release")
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

    // In-app locale switching needs both bundled translations available offline.
    bundle.language.enableSplit = false
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
    implementation(libs.androidx.profileinstaller)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
