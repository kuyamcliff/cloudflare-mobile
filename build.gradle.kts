// AGP 9's built-in Kotlin support defaults to an older Kotlin Gradle Plugin version;
// this raises it to match the Compose compiler / KSP versions pinned in the version
// catalog. See https://kotl.in/gradle/agp-built-in-kotlin.
buildscript {
    dependencies {
        // Keep in sync with `kotlin` in gradle/libs.versions.toml - can't reference the
        // version catalog here since buildscript{} resolves before it's available.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
