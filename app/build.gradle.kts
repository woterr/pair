import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Where the release signing properties live, if they exist at all.
//
// Read once, here, and deliberately tolerant of absence: the file is gitignored because it holds
// a keystore password, and its absence must produce a clear unsigned build rather than a
// configuration error that stops everything. `keystore.properties` sits beside this file rather
// than in the home directory so the whole release setup is in one place and obviously
// discoverable.
val keystorePropertiesFile = rootProject.file("keystore.properties")

android {
    namespace = "com.wood.pair"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wood.pair"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Bumped for every shipped build. Android refuses to install over an existing app whose
        // versionCode is not higher, and it does so with a bare "INSTALL_FAILED_VERSION_DOWNGRADE"
        // that says nothing about the real cause — so this has to move, and the fact that a
        // straight upgrade works depends on it.
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Release signing, read from properties rather than committed.
    //
    // This is what makes an update an *update*. Android treats an app as the same app only when
    // the package name and the signing certificate both match; if the certificate differs, the
    // install fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE and the only way forward is to
    // uninstall — which is exactly what loses someone's room, their name and their settings.
    //
    // A debug-signed release build gets a fresh throwaway key every time, so every release is a
    // different app as far as the installer is concerned. The key has to be a real, kept one.
    //
    // The properties file is gitignored (see `keystore.properties` in .gitignore). When it is
    // absent the release build falls back to no signing config, which produces an unsigned APK
    // that cannot be installed at all — a loud failure rather than a subtly different identity.
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            val keystore = Properties().apply {
                keystorePropertiesFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
                // v1 and v2: v2 is what Android 7+ uses, and v1 keeps the APK installable on
                // the older devices this module still supports.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    // --- AndroidX core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // --- Lifecycle ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- DataStore ---
    implementation(libs.androidx.datastore.preferences)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Play services (geofencing) ---
    implementation(libs.play.services.location)

    // --- Firebase (versions supplied by BoM) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.common)

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Unit tests ---
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)

    // --- Instrumented tests ---
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
