package io.github.ultrafeed.app4pda.feature.details.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.text.htmlEncode
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.packages.AbiUtils
import io.github.ultrafeed.app4pda.data.forum.ForumDocumentParser
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.googleplay.PlayAppDetails
import io.github.ultrafeed.app4pda.data.rustore.RuStoreDetails
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.DetailsSection
import io.github.ultrafeed.app4pda.feature.details.ForumFileFormatter

internal fun forumFiles(
    context: Context,
    info: ForumInfo?,
    pack: String,
    installedVersion: Long,
    checkVersionCode: Boolean,
): List<DetailsFile> {
    return info
        ?.files
        .orEmpty()
        .filter { it.fileName.endsWith(".apk", ignoreCase = true) }
        .distinctBy { it.fileId }
        .take(1)
        .map { file ->
            val update =
                file.fileVersionCode > installedVersion &&
                    file.packageName == pack &&
                    file.supportsAbi(
                        AbiUtils.getAbiFlags(
                            Build.SUPPORTED_ABIS,
                        ),
                    ) &&
                    file.minSdk <= Build.VERSION.SDK_INT &&
                    (!checkVersionCode || file.hasRealVersionCode)
            DetailsFile(
                "forum:${file.fileId}:${file.downloadUrl}",
                file.fileName,
                file.version,
                file.displaySize,
                file.downloadUrl,
                file.topicId,
                file.postId,
                ForumFileFormatter.describe(file, context),
                update,
            )
        }
}

internal fun playSection(context: Context, info: PlayAppDetails): DetailsSection {
    return DetailsSection(
        context.getString(R.string.source_google_play),
        ForumDocumentParser.parse(
            info.description +
                "<br><br>" +
                info.changes +
                "<br><br>" +
                (if (!info.free) context.getString(R.string.play_paid)
                    else if (info.restricted) context.getString(R.string.play_restricted) else "")
                    .htmlEncode(),
            baseUrl = "https://play.google.com/",
        ),
        info.screenshots,
    )
}

/** Refreshing a topic must not temporarily remove already confirmed store pages. */
internal fun LoadedDetails.withPreviousStores(
    previous: LoadedDetails?,
    language: String,
): LoadedDetails {
    val retained =
        previous?.takeIf {
            it.data.topicId == data.topicId && it.data.packageName == data.packageName
        } ?: return this
    val play =
        data.market
            ?: retained.data.market?.takeIf {
                !it.error && retained.context.playLanguage == language
            }
    val store = data.ruStore ?: retained.data.ruStore?.takeIf { !it.error }
    val previousPlay = data.market == null && play != null
    val previousStore = data.ruStore == null && store != null
    return copy(
        data =
            data.copy(
                market = play,
                ruStore = store,
                files =
                    (data.files +
                            retained.data.files.filter {
                                (it.market && previousPlay) || (it.ruStore && previousStore)
                            })
                        .distinctBy { it.id },
            ),
        context =
            context.copy(
                playPackage =
                    (context.playPackage ?: retained.context.playPackage).takeIf { play != null },
                playLanguage = language.takeIf { play != null }.orEmpty(),
            ),
    )
}

internal fun ruStoreSection(context: Context, info: RuStoreDetails) =
    DetailsSection(
        context.getString(R.string.source_rustore),
        info.fullDescription.let {
            it.htmlEncode().replace("\n", "<br>")
        } + "<br><br>" + info.whatsNew.htmlEncode().replace("\n", "<br>"),
        info.fileUrls.filter { it.type == "SCREENSHOT" }.sortedBy { it.ordinal }.map { it.fileUrl },
    )

internal fun LoadedDetails.withStorePages(
    context: Context,
    play: PlayAppDetails?,
    ruStore: RuStoreDetails?,
    installedVersion: Long,
    language: String,
): LoadedDetails {
    val loaded = this
    val files = loaded.data.files.toMutableList()
    play?.let { playFile(context, it, installedVersion)?.let(files::add) }
    ruStore?.let { files += ruStoreFile(context, it) }
    return loaded.copy(
        data =
            loaded.data.copy(
                market = play?.let { playSection(context, it) },
                ruStore = ruStore?.let { ruStoreSection(context, it) },
                files = files,
            ),
        context =
            loaded.context.copy(
                playPackage = play?.release?.packageName,
                playLanguage = play?.let { language }.orEmpty(),
            ),
    )
}

internal fun playFile(
    context: Context,
    play: PlayAppDetails,
    installedVersion: Long,
): DetailsFile? =
    if (!play.free || play.restricted) null
    else
        DetailsFile(
            "market",
            play.name,
            play.release.versionName,
            play.size
                .takeIf { it > 0 }
                ?.let {
                    DisplayFormats.fileSize(context, it)
                }
                .orEmpty(),
            "",
            0,
            0,
            play.name,
            play.release.versionCode > installedVersion,
            true,
            packageName = play.release.packageName,
        )

internal fun ruStoreFile(
    context: Context,
    ruStore: RuStoreDetails,
): DetailsFile =
    DetailsFile(
        "rustore",
        ruStore.appName,
        ruStore.versionName,
        ruStore.fileSize
            .takeIf { it > 0 }
            ?.let {
                DisplayFormats.fileSize(context, it)
            }
            .orEmpty(),
        "",
        0,
        0,
        ruStore.appName,
        false,
        ruStore = true,
        packageName = ruStore.packageName,
    )

internal fun Context.installedVersionCode(packageName: String): Long =
    try {
        PackageInfoCompat.getLongVersionCode(
            packageManager.getPackageInfo(packageName, 0),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        0L
    }

internal fun Context.failedStoreSection(
    title: String,
    previous: DetailsSection?,
    failure: Throwable,
): DetailsSection {
    val problem = SourceProblem.from(this, title, failure)
    return previous?.copy(problem = problem)
        ?: DetailsSection(title, "", error = true, problem = problem)
}
