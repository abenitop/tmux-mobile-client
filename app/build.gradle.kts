plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
    // Encrypted single-connection storage (Android Keystore-backed). 1.1.0 is the
    // current stable; EncryptedSharedPreferences/MasterKey are soft-deprecated in this
    // line but still shipped and functional -- the MVP plan deliberately uses them over
    // a raw-Tink migration to stay small.
    implementation("androidx.security:security-crypto:1.1.0")
    // Terminal rendering. Not on Maven Central; JitPack only. terminal-view's POM
    // declares terminal-emulator (which carries the native libtermux.so for all 4
    // ABIs) at compile scope, so it arrives transitively.
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
    // sshj declares BouncyCastle as a runtime-scope dep, so it isn't on the compile
    // classpath. SshSpikeSession replaces Android's stripped "BC" provider with this
    // one (Android's has no X25519). Version pinned to sshj 0.41.1's own resolution.
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    // Host storage. KSP resolves to 2.3.12: there is no KSP build for Kotlin 2.4.x yet,
    // and 2.3.12 is verified working against this project's Kotlin 2.4.20 (kspDebugKotlin
    // runs and Room generates its implementations).
    implementation("androidx.room:room-runtime:2.8.5")
    // room-ktx supplies the Flow return type and coroutine support for the DAO.
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Hosts -> Host form -> Session graph.
    implementation("androidx.navigation:navigation-compose:2.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // The Android SDK's org.json is a *stub* on the JVM unit-test classpath (methods
    // throw "not mocked"), so the mappers can't be tested without a real
    // implementation. Test-scope only: the app keeps using the SDK's bundled org.json.
    testImplementation("org.json:json:20250517")
}
