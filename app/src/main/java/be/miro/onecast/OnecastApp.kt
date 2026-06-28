package be.miro.onecast

import android.app.Application
import android.content.Context
import be.miro.onecast.data.AppDatabase
import be.miro.onecast.data.AppSettings
import be.miro.onecast.data.PodcastRepository

/** Process-wide singletons (lightweight manual DI). */
class OnecastApp : Application() {

    private val database by lazy { AppDatabase.get(this) }

    val repository by lazy {
        PodcastRepository(database.podcastDao(), database.episodeDao())
    }

    val settings by lazy { AppSettings.create(this) }
}

/** Convenience accessor for the shared repository from any Context. */
val Context.podcastRepository: PodcastRepository
    get() = (applicationContext as OnecastApp).repository

/** Convenience accessor for the shared app settings from any Context. */
val Context.appSettings: AppSettings
    get() = (applicationContext as OnecastApp).settings
