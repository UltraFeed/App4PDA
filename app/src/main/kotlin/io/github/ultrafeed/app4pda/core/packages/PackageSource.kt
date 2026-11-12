package io.github.ultrafeed.app4pda.core.packages

interface PackageSource {
    fun hasPackageName(): Boolean

    val packageName: String?
}
