// AGP 9's built-in Kotlin uses the Kotlin Gradle Plugin from the buildscript classpath.
buildscript {
    dependencies { classpath(libs.kotlin.gradle) }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/*/kotlin/**/*.kt")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("gradle.properties", "gradle/*.toml", "app/src/**/*.xml", "app/src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("verifyDebug") {
    group = "verification"
    description = "Runs the debug checks and builds the APK."
    dependsOn(
        "spotlessCheck",
        ":app:detektDebug",
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
    )
}
