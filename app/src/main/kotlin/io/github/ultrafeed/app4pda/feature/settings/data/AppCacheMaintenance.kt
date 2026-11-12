package io.github.ultrafeed.app4pda.feature.settings.data

import android.content.Context
import android.database.SQLException
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.data.forum.ForumCache
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogCache
import io.github.ultrafeed.app4pda.data.googleplay.PlayPageCache
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.feature.recent.data.ForumDigest
import io.github.ultrafeed.app4pda.feature.reports.data.CrashReports
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates cache owners without touching persistent data or completed downloads. */
class AppCacheMaintenance(
    context: Context,
    private val forum: ForumCache,
    private val images: AppImages,
    private val reports: CrashReports,
    private val digest: ForumDigest,
    private val pages: SourcePageCaches,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val cleanup = Mutex()

    suspend fun imageSizes(): Map<ImageOrigin, Long?> = images.sizes()

    suspend fun size(): Long =
        withContext(ioDispatcher) {
            val operation = currentCoroutineContext()
            pages.ruStore.cacheSize() +
                roots().sumOf { root ->
                    root
                        .walkTopDown()
                        .onEnter { it == root || (it.name != "downloads" && inside(it, root)) }
                        .filter {
                            operation.ensureActive()
                            it.isFile && inside(it, root)
                        }
                        .sumOf { it.length() }
                }
        }

    suspend fun clear(): Unit = cleanup.withLock {
        val failures = mutableListOf<Exception>()
        suspend fun attempt(owner: String, action: suspend () -> Unit) {
            val started = SystemClock.elapsedRealtime()
            Log.d("CacheClear", "$owner started")
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                failures += failure
            } catch (failure: SQLException) {
                failures += failure
            } catch (failure: IllegalStateException) {
                failures += failure
            } catch (failure: SecurityException) {
                failures += failure
            } finally {
                Log.d(
                    "CacheClear",
                    "$owner completed after ${SystemClock.elapsedRealtime() - started}ms",
                )
            }
        }
        attempt("images") { images.clear() }
        attempt("forum") { forum.clear() }
        attempt("catalog") { pages.catalog.clear() }
        attempt("digest") { digest.clearCache() }
        attempt("RuStore") { pages.ruStore.clearCache() }
        attempt("Google Play") { pages.play.clear() }
        attempt("WebView") {
            withContext(Dispatchers.Main) {
                val browser = WebView(context)
                try {
                    browser.clearCache(true)
                } finally {
                    browser.destroy()
                }
            }
        }
        attempt("reports") { withContext(ioDispatcher) { reports.cache.clear() } }
        attempt("temporary files") { withContext(ioDispatcher) { clearTemporaryFiles() } }
        if (failures.isNotEmpty()) {
            val error = IOException("Cache cleanup incomplete", failures.first())
            failures.drop(1).forEach(error::addSuppressed)
            throw error
        }
    }

    private fun roots(): List<File> =
        (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).distinctBy {
            it.canonicalPath
        }

    private fun inside(file: File, root: File) =
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)

    private suspend fun clearTemporaryFiles() {
        val operation = currentCoroutineContext()
        fun remove(file: File, root: File) {
            operation.ensureActive()
            check(inside(file, root)) { "Cache path outside its owner" }
            if (file.isDirectory) file.listFiles().orEmpty().forEach { remove(it, root) }
            check(!file.exists() || file.delete()) { "Cache file could not be removed" }
        }
        roots().forEach { root ->
            root
                .listFiles()
                .orEmpty()
                .filterNot {
                    it.name in
                        setOf(
                            "downloads",
                            "images",
                            "forum-cache",
                            "crash-reports",
                            "WebView",
                            "play-pages",
                            "catalog-pages",
                            "digest.json",
                            "digest.checked",
                            "digest-topics.json",
                        )
                }
                .forEach { remove(it, root) }
        }
    }
}

/** Store cache owners have separate journals/generations and are cleared independently. */
data class SourcePageCaches(
    val play: PlayPageCache,
    val ruStore: RuStoreRepository,
    val catalog: ForumCatalogCache,
)
