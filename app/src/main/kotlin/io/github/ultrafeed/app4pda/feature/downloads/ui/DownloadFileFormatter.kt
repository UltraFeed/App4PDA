package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.content.Context
import io.github.ultrafeed.app4pda.data.forum.ForumFileInfo
import io.github.ultrafeed.app4pda.feature.details.ForumFileFormatter
import io.github.ultrafeed.app4pda.feature.downloads.DownloadableInfo

object DownloadFileFormatter {
    @JvmStatic
    fun describe(file: DownloadableInfo, context: Context): CharSequence =
        when (file) {
            is ForumFileInfo -> ForumFileFormatter.describe(file, context)
            else ->
                listOfNotNull(
                        file.name,
                        file.fileName,
                        file.version,
                        file.displaySize,
                    )
                    .joinToString("\n")
        }
}
