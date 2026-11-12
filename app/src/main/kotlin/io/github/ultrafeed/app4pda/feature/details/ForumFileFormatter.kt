package io.github.ultrafeed.app4pda.feature.details

import android.content.Context
import android.os.Build
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.packages.AbiUtils
import io.github.ultrafeed.app4pda.data.forum.ForumFileInfo
import java.util.Locale

object ForumFileFormatter {
    fun describe(file: ForumFileInfo, context: Context): String = buildString {
        fun line(label: Int, value: String) {
            if (value.isNotBlank())
                append(context.getString(label)).append(": ").append(value).append('\n')
        }
        line(R.string.info_name, file.name)
        line(R.string.info_filename, file.fileName)
        if (file.version.isNotBlank()) line(R.string.info_version, file.version)
        line(R.string.info_size, file.displaySize)
        if (file.downloadCount.isNotBlank())
            append(context.getString(R.string.downloads_count, file.downloadCount)).append("\n")
        line(R.string.info_package_name, file.packageName)
        if (file.minSdk > 0)
            line(
                R.string.info_mid_sdk,
                context.getString(
                    R.string.format_android_abi,
                    DisplayFormats.androidVersion(file.minSdk),
                    AbiUtils.formatAbiFlags(file.flags),
                ),
            )
        if (file.crc32 > 0)
            append(
                    context.getString(
                        R.string.format_crc32,
                        file.crc32.toString(HEXADECIMAL_RADIX).uppercase(Locale.ROOT),
                    ),
                )
                .append('\n')
        if (file.hasSignatureStatus(0))
            append(context.getString(R.string.downloads_signature_no_match)).append('\n')
        if (
            !file.supportsAbi(
                AbiUtils.getAbiFlags(
                    Build.SUPPORTED_ABIS,
                ),
            )
        )
            append(context.getString(R.string.downloads_architecture_no_match)).append('\n')
        if (file.minSdk > Build.VERSION.SDK_INT)
            append(context.getString(R.string.downloads_sdk_wrong)).append('\n')
    }
        .trim()
}

private const val HEXADECIMAL_RADIX = 16
