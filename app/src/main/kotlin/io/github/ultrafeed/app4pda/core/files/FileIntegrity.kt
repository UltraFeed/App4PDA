package io.github.ultrafeed.app4pda.core.files

import java.io.File
import java.lang.Long.toUnsignedString
import net.jpountz.xxhash.XXHashFactory
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source

/** Verifies only metadata supplied by the source; an absent hash does not invent a guarantee. */
internal fun verifyFile(file: File, size: Long, checksum: String, sha256: Boolean = true) {
    check(file.isFile && (size <= 0 || file.length() == size)) {
        "Incomplete downloaded file: ${file.name}"
    }
    if (checksum.isBlank()) return
    val hashing =
        if (sha256) HashingSource.sha256(file.source()) else HashingSource.sha1(file.source())
    val hash =
        hashing.buffer().use { source ->
            source.readAll(blackholeSink())
            hashing.hash
        }
    val encoded = checksum.trimEnd('=').replace('+', '-').replace('/', '_')
    check(checksum.equals(hash.hex(), true) || encoded == hash.base64Url().trimEnd('=')) {
        "Downloaded file checksum mismatch: ${file.name}"
    }
}

/** RuStore delivery hashes use XXH64 with seed zero, not SHA-256. */
internal fun verifyRuStoreFile(file: File, size: Long, checksum: String) {
    verifyFile(file, size, "")
    if (checksum.isBlank()) return
    val encoded = checksum.trim()
    val expected =
        if (encoded.matches(Regex("[a-fA-F0-9]{16}"))) encoded.decodeHex()
        else checkNotNull(encoded.decodeBase64()) { "Invalid RuStore checksum" }
    check(expected.size == Long.SIZE_BYTES) { "Unsupported RuStore checksum" }
    val actual =
        ruStoreHashFactory.newStreamingHash64(0L).use { hash ->
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = input.read(buffer)
                while (count >= 0) {
                    hash.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            toUnsignedString(hash.value, 16).padStart(Long.SIZE_BYTES * 2, '0')
        }
    check(actual == expected.hex()) { "Downloaded file checksum mismatch: ${file.name}" }
}

private val ruStoreHashFactory by lazy { XXHashFactory.safeInstance() }
