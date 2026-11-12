plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.detekt)
}

val javaTarget = libs.versions.jdk.get()
val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.ultrafeed.app4pda"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = 0
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "io.github.ultrafeed.app4pda"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("app.versionCode").get().toInt()
        versionName = providers.gradleProperty("app.versionName").get()
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(javaTarget)
        targetCompatibility = JavaVersion.toVersion(javaTarget)
    }

    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures { compose = true }
    bundle { language { enableSplit = false } }
    packaging { resources { excludes += "META-INF/DEPENDENCIES" } }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(javaTarget.toInt()) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    basePath.set(rootProject.projectDir)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set(javaTarget)
}

koinCompiler {
    compileSafety = true
    strictSafety = true
}

dependencies {
    implementation(libs.ketch)

    // UI and navigation.
    implementation(libs.browser)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.material.kolor)
    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.savedstate.compose)
    implementation(libs.annotation)
    implementation(libs.core)

    // Lifecycle and coroutines.
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Persistence and Java API desugaring.
    coreLibraryDesugaring(libs.desugar.nio)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.serialization.json)

    // Forum and store protocols use distinct transport policies.
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.okio)
    implementation(libs.lz4)
    implementation(libs.aurora.play)
    implementation(libs.protobuf.lite)

    // Images.
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)

    // Dependency injection and background work.
    implementation(libs.work.runtime)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.work)

    // Compiler processors.
    ksp(libs.room.compiler)

    // Pure Kotlin/JVM unit tests.
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
}
