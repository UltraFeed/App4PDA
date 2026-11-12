package io.github.ultrafeed.app4pda.app.di

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.ketch.DownloadConfig
import com.ketch.Ketch
import com.ketch.NotificationConfig
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppDispatchers
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.network.ForumPageHttpTransport
import io.github.ultrafeed.app4pda.core.network.ForumPageTransport
import io.github.ultrafeed.app4pda.core.network.NetworkUtils
import io.github.ultrafeed.app4pda.core.network.createForumHttpClient
import io.github.ultrafeed.app4pda.core.network.createForumPagesClient
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.database.DownloadRecordsDao
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.forum.ForumApiClient
import io.github.ultrafeed.app4pda.data.forum.ForumCache
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogCache
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogPageSource
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogPages
import io.github.ultrafeed.app4pda.data.forum.ForumPackageLookup
import io.github.ultrafeed.app4pda.data.forum.ForumPostRepository
import io.github.ultrafeed.app4pda.data.forum.ForumRequests
import io.github.ultrafeed.app4pda.data.forum.ForumSearchSort
import io.github.ultrafeed.app4pda.data.forum.ForumTransports
import io.github.ultrafeed.app4pda.data.forum.findForumPackage
import io.github.ultrafeed.app4pda.data.googleplay.AnonymousPlaySessionProvider
import io.github.ultrafeed.app4pda.data.googleplay.GooglePlayRepository
import io.github.ultrafeed.app4pda.data.googleplay.PlayHttpClient
import io.github.ultrafeed.app4pda.data.googleplay.PlayPackageInstaller
import io.github.ultrafeed.app4pda.data.googleplay.PlayPageCache
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.createSettingsDataStore
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthHttp
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthRepository
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthTransport
import io.github.ultrafeed.app4pda.feature.auth.ui.ForumAuthViewModel
import io.github.ultrafeed.app4pda.feature.catalog.CatalogQuery
import io.github.ultrafeed.app4pda.feature.catalog.data.CatalogRepository
import io.github.ultrafeed.app4pda.feature.catalog.ui.CatalogViewModel
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.details.data.AppDetailsRepository
import io.github.ultrafeed.app4pda.feature.details.data.DetailsForum
import io.github.ultrafeed.app4pda.feature.details.ui.AppDetailsViewModel
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadLocator
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsRepository
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsStorage
import io.github.ultrafeed.app4pda.feature.downloads.ui.AttachmentViewModel
import io.github.ultrafeed.app4pda.feature.downloads.ui.DownloadsViewModel
import io.github.ultrafeed.app4pda.feature.forum.ui.ForumPostViewModel
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.data.ForumBindings
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsStore
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledUpdateSources
import io.github.ultrafeed.app4pda.feature.installed.data.UpdateWorker
import io.github.ultrafeed.app4pda.feature.installed.ui.InstalledAppsViewModel
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLookupRepository
import io.github.ultrafeed.app4pda.feature.lookup.ui.AppLookupViewModel
import io.github.ultrafeed.app4pda.feature.recent.data.ForumDigest
import io.github.ultrafeed.app4pda.feature.recent.ui.DigestViewModel
import io.github.ultrafeed.app4pda.feature.reports.data.CrashReports
import io.github.ultrafeed.app4pda.feature.settings.data.AppCacheMaintenance
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsBackupRepository
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import io.github.ultrafeed.app4pda.feature.settings.data.SourcePageCaches
import io.github.ultrafeed.app4pda.feature.settings.ui.ForumAccountsViewModel
import io.github.ultrafeed.app4pda.feature.settings.ui.SettingsBackupViewModel
import io.github.ultrafeed.app4pda.feature.settings.ui.SettingsViewModel
import io.github.ultrafeed.app4pda.feature.updates.SelfUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.worker

internal val infrastructureModule = module {
    single { AppDispatchers() }
    single<CoroutineDispatcher> {
        get<AppDispatchers>().io
    }
    single<CoroutineDispatcher>(named("io")) {
        get<AppDispatchers>().io
    }
    single(named("settingsScope")) {
        CoroutineScope(
            SupervisorJob() + get<AppDispatchers>().io,
        )
    } onClose { it?.let { scope -> scope.coroutineContext[Job]?.cancel() } }
    single<DataStore<Preferences>> {
        createSettingsDataStore(
            androidContext(),
            get(named("settingsScope")),
        )
    }
    single { AppSettings(get(), get(), get(named("settingsScope"))) } onClose { it?.close() }
    single { PackageInspector(androidContext(), get(named("io"))) }
    single<DownloadsStorage>()
    single<PlayPackageInstaller>()
    single<OkHttpClient> { OkHttpClient() }
    single { SelfUpdate(get(), get(), get(), get(), get(), get(named("io"))) } onClose
        {
            it?.close()
        }
    single(named("forum")) { createForumHttpClient() } onClose { it?.close() }
    single(named("forumPages")) { createForumPagesClient() } onClose { it?.close() }
    single<PlayHttpClient>()
    single {
        Room.databaseBuilder(androidContext(), LocalDatabase::class.java, "local-catalog.db")
            .build()
    } onClose { it?.close() }
    single { get<LocalDatabase>().users() }
    single<DownloadRecordsDao> {
        get<LocalDatabase>().users()
    }
    single { get<LocalDatabase>().forum() }
    single { ForumCache(androidContext(), get(named("io"))) } onClose { it?.close() }
    single { AppImages(androidContext(), get(), get(), get()) } onClose { it?.close() }
    single { CrashReports(androidContext()) } onClose { it?.close() }
}

internal val dataModule = module {
    single { PlayPageCache(androidContext()) }
    single {
        ForumRequests(
            get(),
            SystemClock::elapsedRealtime,
            System::currentTimeMillis,
        )
    }
    single {
        ForumApiClient(
            androidContext(),
            get(),
            get(),
            get(),
            ForumTransports(
                get(named("forumPages")),
                get(),
            ) {
                NetworkUtils.isNetworkConnected(androidContext())
            },
        )
    }
    single { AnonymousPlaySessionProvider(androidContext(), get()) }
    single { GooglePlayRepository(get(), get(), get(named("io"))) }
    single { RuStoreRepository(get(), get(), get()) }
    single {
        val packages = get<PackageInspector>()
        InstalledAppsStore(
            get(),
            get(),
            packageScan = packages::scanInstalledPackages,
            packageInstalled = packages::isInstalled,
            packageHasOwnIcon = packages.artifacts::hasOwnIcon,
        )
    }
    single {
        val forum = get<ForumApiClient>()
        ForumBindings(
            get(),
            get(),
            topicLoad = { topic -> forum.loadTopic(topic, userInitiated = false).info },
            search = { query, next ->
                forum.searchTopics(
                    query,
                    ForumSearchSort.RELEVANCE,
                    next,
                    userInitiated = false,
                )
            },
        )
    }
    single {
        val market = get<GooglePlayRepository>()
        InstalledAppsRepository(
            androidContext(),
            get(),
            get(),
            get(),
            InstalledUpdateSources(
                get(),
                market::versions,
                get(),
            ),
            CoroutineScope(
                SupervisorJob() + get<AppDispatchers>().io,
            ),
        )
    } onClose { it?.close() }
    single {
        DownloadLocator(
            androidContext(),
            get(),
            get(),
            get(),
            get(named("forumPages")),
            get<ForumRequests>(),
        )
    }
    single<Ketch> {
        Ketch.builder()
            .setOkHttpClient(get<OkHttpClient>())
            .setDownloadConfig(
                DownloadConfig(
                    AppPolicy.HTTP_CONNECT_MILLIS,
                    AppPolicy.HTTP_PAGE_READ_MILLIS,
                ),
            )
            .setNotificationConfig(
                NotificationConfig(
                    enabled = true,
                    channelName = androidContext().getString(R.string.downloads),
                    smallIcon = R.drawable.ic_launcher_foreground,
                ),
            )
            .build(androidContext())
    }
    single {
        DownloadController(
            androidContext(),
            get<LocalDatabase>().users(),
            get(),
            get(named("io")),
            get<DownloadLocator>(),
            get<Ketch>(),
        )
    } onClose { it?.close() }
    single { DownloadsRepository(get(), get(), get(), get(), get(), get(named("io"))) }
    single<ForumDigest>()
    single {
        SettingsBackupRepository(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(named("io")),
        )
    }
    viewModel { SettingsBackupViewModel(get()) }
    single<SettingsRepository>() onClose { it?.close() }
    single {
        SourcePageCaches(get(), get(), get())
    }
    single<AppCacheMaintenance>()
    single<CatalogRepository>()
    single<ForumCatalogCache>()
    single<ForumCatalogPages> {
        ForumCatalogPageSource(get(), get())
    }
    single<AppLookupRepository>()
    single<ForumPackageLookup> {
        val api = get<ForumApiClient>()
        ForumPackageLookup { pack ->
            findForumPackage(
                pack,
                { query, next ->
                    api.searchTopics(
                        query,
                        ForumSearchSort.RELEVANCE,
                        next,
                    )
                },
                { api.loadTopic(it).info },
                { true },
            )
        }
    }
    single<ForumPageTransport> { ForumPageHttpTransport(get(named("forum"))) }
    single<ForumAuthTransport> { ForumAuthHttp(get(named("forum"))) }
    single { ForumAuthRepository(androidContext(), get(), get(), get(named("io")), get()) }
    single<ForumPostRepository>()
    // Card loaders are scoped to their ViewModel; no shared mutable per-card state.
    factory<DetailsForum>()
    factory<AppDetailsRepository>()
    worker<UpdateWorker>()
}

internal val screenModule = module {
    viewModel { (section: AppSection) ->
        InstalledAppsViewModel(
            get(),
            get(),
            section,
            get<AppSettings>().current.appsSortOrder,
            get(),
            get(),
        )
    }
    viewModel { (query: CatalogQuery) ->
        CatalogViewModel(get(), get(), query)
    }
    viewModel { (topic: Int, pack: String, source: DetailsTab) ->
        AppDetailsViewModel(get<AppDetailsRepository>(), get(), topic, pack, source)
    }
    viewModel { DownloadsViewModel(get<DownloadsRepository>(), get()) }
    viewModel { (url: String, topic: Int) ->
        AttachmentViewModel(
            get(),
            get(),
            url,
            topic,
        )
    }
    viewModel { DigestViewModel(get(), get()) }
    viewModel { ForumAuthViewModel(get()) }
    viewModel { (topic: Int, post: Int) -> ForumPostViewModel(get(), topic, post) }
    viewModel { (packageName: String) -> AppLookupViewModel(get(), get(), packageName) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ForumAccountsViewModel(get(), get()) }
}
