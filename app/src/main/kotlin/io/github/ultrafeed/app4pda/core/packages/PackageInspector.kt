package io.github.ultrafeed.app4pda.core.packages

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageInspector(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val packageManager = context.applicationContext.packageManager
    val artifacts: PackageArtifacts =
        PackageArtifacts(this.context, ::installedPackage, ioDispatcher)

    /** A complete PackageManager snapshot. Display filters never remove packages from storage. */
    fun scanInstalledPackages(): Map<String, LocalPackage> {
        val launchable =
            packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                )
                .mapTo(hashSetOf()) { it.activityInfo.packageName }
        val installed = packageManager.getInstalledPackages(0)
        return installed.associate { info ->
            val flags = info.applicationInfo?.flags ?: 0
            info.packageName to
                LocalPackage(
                    info.applicationInfo?.loadLabel(packageManager)?.toString().orEmpty(),
                    info.versionName ?: PackageInfoCompat.getLongVersionCode(info).toString(),
                    PackageInfoCompat.getLongVersionCode(info),
                    info.lastUpdateTime,
                    flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    info.applicationInfo?.minSdkVersion ?: 1,
                    0,
                    launchable = info.packageName in launchable,
                )
        }
    }

    private fun installedPackage(packageName: String, flags: Int = 0): PackageInfo? =
        try {
            packageManager.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    fun getApplicationName(packageName: String): String =
        installedPackage(packageName)
            ?.applicationInfo
            ?.loadLabel(packageManager)
            ?.toString()
            .orEmpty()

    /** Resolve the forum query in Russian without changing the app or device locale. */
    suspend fun bindingSearchName(packageName: String, currentName: String): String =
        withContext(ioDispatcher) {
            val label =
                try {
                    val info = installedPackage(packageName)?.applicationInfo
                    if (info == null) null
                    else if (info.nonLocalizedLabel != null) info.nonLocalizedLabel.toString()
                    else if (info.labelRes != 0) {
                        val target = context.createPackageContext(packageName, 0)
                        val configuration = Configuration(target.resources.configuration)

                        ConfigurationCompat.setLocales(
                            configuration,
                            LocaleListCompat.forLanguageTags("ru"),
                        )

                        target
                            .createConfigurationContext(configuration)
                            .getText(info.labelRes)
                            .toString()
                    } else null
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: Resources.NotFoundException) {
                    null
                }
            label?.trim()?.takeIf { it.isNotEmpty() } ?: currentName.trim().ifBlank { packageName }
        }

    fun isInstalled(packageName: String): Boolean = installedPackage(packageName) != null
}
