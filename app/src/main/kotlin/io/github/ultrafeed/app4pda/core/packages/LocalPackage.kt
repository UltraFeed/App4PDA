package io.github.ultrafeed.app4pda.core.packages

/** Immutable snapshot of an installed package, independent of presentation and storage. */
data class LocalPackage(
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val installedAt: Long,
    val system: Boolean,
    val minimumSdk: Int,
    val abiFlags: Int,
    val oldInstallation: Boolean = false,
    val launchable: Boolean = true,
)
