import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Release signing configuration.
//
// The release upload key is resolved from one of two sources, in priority order:
//   1. A gitignored `keystore.properties` at the repo root (local developer
//      machines) with keys: storeFile, storePassword, keyAlias, keyPassword.
//   2. Environment variables (CI secrets):
//      CM_KEYSTORE_PATH, CM_KEYSTORE_PASSWORD, CM_KEY_ALIAS, CM_KEY_PASSWORD.
//
// If neither source provides a complete, valid keystore, the release build
// transparently falls back to the debug signing config so that
// assembleRelease / bundleRelease always succeed (useful for CI verification
// builds). `releaseSigningReady` records which path was taken.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

fun resolveSigningValue(propKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val cmStoreFilePath = resolveSigningValue("storeFile", "CM_KEYSTORE_PATH")
val cmStorePassword = resolveSigningValue("storePassword", "CM_KEYSTORE_PASSWORD")
val cmKeyAlias = resolveSigningValue("keyAlias", "CM_KEY_ALIAS")
val cmKeyPassword = resolveSigningValue("keyPassword", "CM_KEY_PASSWORD")

val resolvedStoreFile = cmStoreFilePath?.let { rootProject.file(it) }
val releaseSigningReady =
    resolvedStoreFile != null && resolvedStoreFile.exists() &&
        !cmStorePassword.isNullOrBlank() &&
        !cmKeyAlias.isNullOrBlank() &&
        !cmKeyPassword.isNullOrBlank()

android {
    namespace = "com.chingfordmosque.prayertimes.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chingfordmosque.prayertimes"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("CM_VERSION_CODE")?.toInt() ?: 13)
        versionName = (System.getenv("CM_VERSION_NAME") ?: "1.2.3")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = resolvedStoreFile
                storePassword = cmStorePassword
                keyAlias = cmKeyAlias
                keyPassword = cmKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            println(
                if (releaseSigningReady) {
                    "[ChingfordMosque] Release build signed with the real upload key (keystore.properties/env)."
                } else {
                    "[ChingfordMosque] No release keystore found; release build signed with the DEBUG key (fallback)."
                }
            )
        }

        debug {
        }
    }

    applicationVariants.all {
        val variant = this
        val verName = defaultConfig.versionName ?: "1.2.3"
        outputs.all {
            val outputImpl = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.name == "release") {
                outputImpl?.outputFileName = "ChingfordMosqueV${verName}.apk"
            } else {
                outputImpl?.outputFileName = "ChingfordMosqueV${verName}-${variant.name}.apk"
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    // Compose, versioned by the BOM.
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidX runtime / lifecycle / activity.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Material components (XML base theme only).
    implementation("com.google.android.material:material:1.12.0")

    // Persistence, networking, coroutines.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Background work scheduling (periodic refresh).
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
