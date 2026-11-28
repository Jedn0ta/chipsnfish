plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.onoseto.chipsnfish"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onoseto.chipsnfish"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("shared") {
            // Shared test key so new builds install over old ones. Replace with a private key before Google Play.
            storeFile = file("keystore/chipsnfish-debug.jks")
            storePassword = "chipsnfish"
            keyAlias = "chipsnfish"
            keyPassword = "chipsnfish"
        }
        // Private upload key for Google Play (from GitHub secrets, never in the repo).
        val up = System.getenv("UPLOAD_KEYSTORE")
        if (!up.isNullOrBlank() && file(up).exists()) create("upload") {
            storeFile = file(up)
            storePassword = System.getenv("UPLOAD_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("UPLOAD_PASSWORD")
        }
    }

    flavorDimensions += "dist"
    productFlavors {
        // APK on GitHub: updates itself from the GitHub release
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            signingConfig = signingConfigs.getByName("shared")
        }
        // Google Play: updates come from the Play Store only
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("shared")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // "Sign in with Google": one profile per person that survives reinstalls
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5") // app-update pulls an old fragment otherwise

    testImplementation("junit:junit:4.13.2")
}
