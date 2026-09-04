pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Local patched imgui-kmp (glyph-range API with static storage);
        // takes precedence over the Central 1.0.12 artifact.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "CrystalDiskMark"

include(":app")
include(":android")
