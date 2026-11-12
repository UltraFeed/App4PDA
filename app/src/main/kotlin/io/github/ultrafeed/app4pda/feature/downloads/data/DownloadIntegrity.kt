package io.github.ultrafeed.app4pda.feature.downloads.data

import io.github.ultrafeed.app4pda.core.files.verifyFile
import io.github.ultrafeed.app4pda.core.files.verifyRuStoreFile
import io.github.ultrafeed.app4pda.data.database.DownloadRecord
import java.io.File

internal fun DownloadRecord.verify(file: File) {
    if (source == "rustore") {
        verifyRuStoreFile(file, expectedBytes, checksum)
        return
    }
    val sha1 = checksum.matches(Regex("[a-fA-F0-9]{40}")) || checksum.trimEnd('=').length == 27
    verifyFile(file, expectedBytes, checksum, sha256 = !sha1)
}
