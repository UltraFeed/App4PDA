package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import java.io.File
import java.util.Locale

/** Validated file information; Android launchers remain owned by the screen. */
internal class ReadyDownloadFile(context: Context, path: String, val installable: Boolean) {
    val file = File(path)

    init {
        check(file.exists()) { context.getString(R.string.downloads_cache_missing) }
    }

    val split = File(file, PlayDownloadGroup.MANIFEST).isFile
    val mimeType: String =
        if (file.extension.equals("apk", true)) {
            if (installable) "application/vnd.android.package-archive"
            else "application/octet-stream"
        } else
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
                ?: "application/octet-stream"

    fun uri(context: Context): Uri {
        check(file.isFile) { context.getString(R.string.downloads_cache_missing) }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
