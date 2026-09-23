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
    // sshj declares BouncyCastle as a runtime-scope dep, so it isn't on the compile
    // classpath. SshSpikeSession replaces Android's stripped "BC" provider with this
    // one (Android's has no X25519). Version pinned to sshj 0.41.1's own resolution.
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    testImplementation("junit:junit:4.13.2")
    // The Android SDK's org.json is a *stub* on the JVM unit-test classpath (methods
    // throw "not mocked"), so the mappers can't be tested without a real
    // implementation. Test-scope only: the app keeps using the SDK's bundled org.json.
    testImplementation("org.json:json:20250517")
}
