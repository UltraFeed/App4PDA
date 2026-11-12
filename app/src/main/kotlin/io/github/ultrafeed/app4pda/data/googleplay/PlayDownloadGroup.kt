package io.github.ultrafeed.app4pda.data.googleplay

import android.util.AtomicFile
import androidx.core.util.writeText
import io.github.ultrafeed.app4pda.core.files.verifyFile
import io.github.ultrafeed.app4pda.core.files.verifyRuStoreFile
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import org.json.JSONArray
import org.json.JSONObject

/** A manifest is written before starting any transfer; signed URLs are never persisted. */
class PlayDownloadGroup
private constructor(
    val directory: File,
    val packageName: String,
    val versionCode: Long,
    val files: List<PlayDownloadFile>,
) {
    private val isRuStore: Boolean
        get() = directory.name.startsWith("rustore-")

    fun file(item: PlayDownloadFile): File =
        File(directory, item.name).also {
            require(
                item.name != MANIFEST &&
                    item.name.isNotBlank() &&
                    it.canonicalFile.parentFile == directory.canonicalFile,
            ) {
                "Invalid delivery file name"
            }
        }

    fun verify(item: PlayDownloadFile) {
        if (isRuStore) {
            verifyRuStoreFile(
                file(item),
                item.size,
                item.sha256,
            )
            return
        }
        verifyFile(
            file(item),
            item.size,
            item.sha256.ifBlank { item.sha1 },
            sha256 = item.sha256.isNotBlank(),
        )
    }

    /** One verification pass for a complete Downloads listing snapshot. */
    internal fun verifiedFiles(
        verifyFile: (PlayDownloadFile) -> Unit = ::verify,
    ): List<Pair<PlayDownloadFile, File>> {
        check(files.any { it.type == PlayFileType.BASE || it.type == PlayFileType.APK }) {
            "Base APK missing"
        }
        return files.map { item ->
            verifyFile(item)
            item to file(item)
        }
    }

    fun apkFiles(): List<File> {
        val apks = files.filter {
            it.type == PlayFileType.BASE ||
                it.type == PlayFileType.SPLIT ||
                it.type == PlayFileType.APK
        }
        check(apks.any { it.type == PlayFileType.BASE || it.type == PlayFileType.APK }) {
            "Base APK missing"
        }
        apks.forEach(::verify)
        return apks.map(::file)
    }

    /** RuStore can wrap each APK with dex optimization profiles. Verify the original first. */
    internal inline fun <T> withInstallApks(cache: File, action: (List<File>) -> T): T {
        val files = apkFiles()
        if (!isRuStore) return action(files)
        val staging = createTempDirectory(cache.toPath(), "rustore-install-").toFile()
        try {
            return action(
                files.mapIndexed { index, file ->
                    unwrapRuStoreApk(file, File(staging, "part-$index.apk"))
                },
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        const val MANIFEST: String = "play-delivery.json"

        fun read(directory: File): PlayDownloadGroup? {
            val manifest = File(directory, MANIFEST)
            if (!manifest.isFile) return null
            return runCatching {
                val json = JSONObject(manifest.readText())
                val array = json.getJSONArray("files")
                PlayDownloadGroup(
                        directory,
                        json.getString("package"),
                        json.getLong("version"),
                        List(array.length()) { index ->
                            val item = array.getJSONObject(index)
                            PlayDownloadFile(
                                item.getString("name"),
                                "",
                                item.getLong("size"),
                                PlayFileType.valueOf(item.getString("type")),
                                item.getString("sha256"),
                                item.getString("sha1"),
                            )
                        },
                    )
                    .also { group -> group.files.forEach(group::file) }
            }
                .getOrNull()
        }

        internal fun create(
            root: File,
            packageName: String,
            version: Long,
            delivery: List<PlayDownloadFile>,
            source: String = "play",
        ): PlayDownloadGroup {
            require(packageName.matches(Regex("[A-Za-z0-9_.]+")) && delivery.isNotEmpty())
            require(delivery.map { it.name }.distinct().size == delivery.size)
            require(source in setOf("play", "rustore"))
            val directory = File(File(root, ".play"), "$source-$packageName-$version")
            check(directory.isDirectory || directory.mkdirs())
            read(directory)?.let { existing ->
                require(existing.files == delivery.map { it.copy(url = "") }) {
                    "Delivery contents changed for the selected release"
                }
                return existing
            }
            val group = PlayDownloadGroup(directory, packageName, version, delivery)
            delivery.forEach(group::file)
            val manifest =
                JSONObject()
                    .put("package", packageName)
                    .put("version", version)
                    .put(
                        "files",
                        JSONArray().apply {
                            delivery.forEach {
                                put(
                                    JSONObject()
                                        .put("name", it.name)
                                        .put("size", it.size)
                                        .put("type", it.type.name)
                                        .put("sha256", it.sha256)
                                        .put("sha1", it.sha1),
                                )
                            }
                        },
                    )
            val atomic = AtomicFile(File(directory, MANIFEST))
            atomic.writeText("$manifest")
            return group
        }
    }
}

internal fun unwrapRuStoreApk(file: File, target: File): File =
    ZipFile(file).use { archive ->
        if (archive.getEntry("AndroidManifest.xml") != null) return@use file
        val apk =
            archive.entries().asSequence().singleOrNull { entry ->
                !entry.isDirectory &&
                    entry.name.endsWith(".apk") &&
                    '/' !in entry.name &&
                    '\\' !in entry.name
            } ?: error("Invalid RuStore APK wrapper")
        archive.getInputStream(apk).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        check(target.length() == apk.size) { "Incomplete RuStore APK wrapper" }
        target
    }
