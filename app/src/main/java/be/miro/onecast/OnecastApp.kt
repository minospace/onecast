package be.miro.onecast

import android.app.Application
import android.content.Context
import be.miro.onecast.data.AppDatabase
import be.miro.onecast.data.AppSettings
import be.miro.onecast.data.PodcastRepository
import be.miro.onecast.download.DownloadStore
import be.miro.onecast.download.EpisodeDownloader
import be.miro.onecast.local.LocalMediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

/** Process-wide singletons (lightweight manual DI). */
class OnecastApp : Application() {

    private val database by lazy { AppDatabase.get(this) }

    private val downloadStore by lazy { DownloadStore(this) }

    private val localStore by lazy { LocalMediaStore(this) }

    val repository by lazy {
        PodcastRepository(
            database.podcastDao(),
            database.episodeDao(),
            database.queueDao(),
            downloadStore,
            localStore,
            // A disk cache lets feed refreshes ask "changed since last time?" and get a tiny 304
            // back instead of re-downloading every feed in full (see FeedFetcher.fetchIfChanged).
            OkHttpClient.Builder()
                .cache(Cache(File(cacheDir, "http"), HTTP_CACHE_BYTES))
                .build(),
        )
    }

    val settings by lazy { AppSettings.create(this) }

    val downloads by lazy { EpisodeDownloader(this, repository, downloadStore) }

    /**
     * For work the user started that must finish even if they leave the screen it was started from
     * (e.g. importing audio files) — an activity's scope would cancel it halfway through.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private companion object {
        const val HTTP_CACHE_BYTES = 50L * 1024 * 1024
    }
}

/** Convenience accessor for the shared repository from any Context. */
val Context.podcastRepository: PodcastRepository
    get() = (applicationContext as OnecastApp).repository

/** Convenience accessor for the shared app settings from any Context. */
val Context.appSettings: AppSettings
    get() = (applicationContext as OnecastApp).settings

/** Convenience accessor for the shared episode downloader from any Context. */
val Context.episodeDownloads: EpisodeDownloader
    get() = (applicationContext as OnecastApp).downloads
