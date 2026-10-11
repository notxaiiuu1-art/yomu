import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so no kotlin-android plugin is applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.yomu"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yomu"
        // Pixel 8a launched on Android 14; 31+ gives Material You colours and the splash API.
        minSdk = 31
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        // The Pixel 8a (Tensor G3) is arm64 only; one ABI keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            // A fixed key, so every new build installs as an update over the previous one.
            // Override with your own key through the YOMU_KEYSTORE_* environment variables.
            storeFile = file(System.getenv("YOMU_KEYSTORE_PATH") ?: "yomu-release.jks")
            storePassword = System.getenv("YOMU_KEYSTORE_PASSWORD") ?: "yomureader"
            keyAlias = System.getenv("YOMU_KEY_ALIAS") ?: "yomu"
            keyPassword = System.getenv("YOMU_KEY_PASSWORD") ?: "yomureader"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            // Extensions are loaded at runtime and call straight into OkHttp, Jsoup, kotlinx,
            // coroutines and the host API by name. R8 can't see those call sites and would
            // strip what they need, so shrinking stays off.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "kotlin-tooling-metadata.json",
            )
        }
        jniLibs { useLegacyPackaging = false }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // ---- Runtime libraries extensions link against (the host app provides them) ----
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.zstd)
    implementation(libs.okhttp.doh)
    implementation(libs.okio)
    implementation(libs.serialization.json)
    implementation(libs.serialization.json.okio)
    implementation(libs.serialization.protobuf)
    implementation(libs.jsoup)
    implementation(libs.injekt)
    implementation(libs.rxjava)
    implementation(libs.quickjs)
    implementation(libs.androidx.preference)
}
