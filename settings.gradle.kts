pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux's terminal-view/terminal-emulator are not on Maven Central; JitPack
        // is the only published source. Verified: v0.118.3 is the latest non-beta tag
        // and JitPack has pre-built both AARs (HTTP 200 on the .pom).
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "tmux-mobile-client-phase0"
include(":app")
