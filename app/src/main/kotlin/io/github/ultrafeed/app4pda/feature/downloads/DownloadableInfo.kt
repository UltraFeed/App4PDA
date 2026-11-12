package io.github.ultrafeed.app4pda.feature.downloads

import io.github.ultrafeed.app4pda.core.packages.PackageSource

interface DownloadableInfo : FileSource, PackageSource {
    val displaySize: String?

    val flags: Int

    val name: String?

    val version: String?

    fun hasSignatureStatus(status: Int): Boolean

    val downloadCount: String?

    val minSdk: Int
}
