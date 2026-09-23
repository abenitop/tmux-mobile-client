plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tmuxmobile.phase0"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tmuxmobile.phase0"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-phase0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.hierynomus:sshj:0.41.1")

    testImplementation("junit:junit:4.13.2")
}
