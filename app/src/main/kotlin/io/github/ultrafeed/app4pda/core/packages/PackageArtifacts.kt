package io.github.ultrafeed.app4pda.core.packages

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** APK metadata, package icons and signer selection share the platform package reader. */
class PackageArtifacts
internal constructor(
    private val context: Context,
    private val installedPackage: (String, Int) -> PackageInfo?,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val packageManager = context.packageManager

    /** Read only the explicitly shared content; URI paths are not filesystem paths. */
    suspend fun sharedApkPackage(uri: Uri): String =
        withContext(ioDispatcher) {
            require(uri.scheme == "content") { "Expected a content URI" }
            val temporary = File.createTempFile("shared-apk-", ".apk", context.cacheDir)
            try {
                val input =
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Shared APK is unavailable")
                input.use { source ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = source.read(buffer)
                            if (size < 0) break
                            output.write(buffer, 0, size)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                readApk(temporary.path, 0)?.packageName?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Invalid APK")
            } finally {
                check(temporary.delete() || !temporary.exists()) {
                    "Unable to remove shared APK copy"
                }
            }
        }

    fun loadPackageIcon(packageName: String): Drawable? =
        try {
            installedPackage(packageName, 0)?.let(::loadApplicationIcon)
        } catch (_: Exception) {
            null
        }

    fun loadApplicationIcon(info: PackageInfo): Drawable? =
        info.applicationInfo?.loadIcon(packageManager)

    fun hasOwnIcon(packageName: String): Boolean {
        val icon = installedPackage(packageName, 0)?.applicationInfo?.icon ?: return true
        return icon != 0 && icon != android.R.drawable.sym_def_app_icon
    }

    fun readApk(path: String, flags: Int): PackageInfo? =
        try {
            packageManager.getPackageArchiveInfo(path, flags)
        } catch (_: Exception) {
            null
        }

    fun getApkApplicationName(info: PackageInfo, path: String): String =
        info.applicationInfo
            ?.run {
                sourceDir = path
                publicSourceDir = path
                "${
                    loadLabel(
                        packageManager,
                    )
                }"
            }
            .orEmpty()

    val signatureQueryFlags: Int
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES

    /** Match the old PackageManager wire contract: the oldest certificate after rotation. */
    fun getSignatures(info: PackageInfo): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.signingInfo?.let {
                if (it.hasMultipleSigners()) it.apkContentsSigners
                else it.signingCertificateHistory?.take(1)?.toTypedArray()
            }
        else info.signatures

    fun getInstalledSignatures(packageName: String): Array<Signature>? =
        installedPackage(packageName, signatureQueryFlags)?.let(::getSignatures)

    fun hasSameInstalledSignatures(packageName: String, signatures: Array<Signature>?): Boolean {
        val installed = getInstalledSignatures(packageName)
        return !installed.isNullOrEmpty() &&
            !signatures.isNullOrEmpty() &&
            installed.toSet() == signatures.toSet()
    }
}
