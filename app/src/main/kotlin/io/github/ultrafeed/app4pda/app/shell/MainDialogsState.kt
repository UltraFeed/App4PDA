package io.github.ultrafeed.app4pda.app.shell

import kotlinx.serialization.Serializable

@Serializable
data class MainDialogsState(
    val downloadUrl: String? = null,
    val openDownloads: Boolean = false,
    val webViewRequired: Boolean = false,
    val permissionsDenied: Boolean = false,
)
