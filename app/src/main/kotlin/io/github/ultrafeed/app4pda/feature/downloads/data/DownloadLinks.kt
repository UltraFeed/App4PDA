package io.github.ultrafeed.app4pda.feature.downloads.data

import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.CRC32

object DownloadLinks {
    fun decodeWindows1251(value: String): String = URLDecoder.decode(value, "windows-1251")

    fun fileName(url: String): String =
        if ('/' in url) decodeWindows1251(url.substringAfterLast('/')) else url

    private fun extractFileId(url: String, prefix: String, suffix: String): Int {
        val prefixIndex = url.indexOf(prefix)
        if (prefixIndex < 0) return -1
        val start = prefixIndex + prefix.length
        val end = url.indexOf(suffix, start + 1)
        return if (end < start) -1 else url.substring(start, end).trim().toIntOrNull() ?: -1
    }

    private fun signedChecksum(bytes: ByteArray): Int =
        CRC32().apply { update(bytes) }.value.toInt()

    fun fileIdOrChecksum(url: String, prefix: String, suffix: String): Int {
        val fileId = extractFileId(url, prefix, suffix)
        return if (fileId == -1) -signedChecksum(url.toByteArray(Charset.defaultCharset()))
        else fileId
    }
}
