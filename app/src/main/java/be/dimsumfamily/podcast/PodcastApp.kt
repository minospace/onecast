package be.dimsumfamily.podcast

import android.app.Application
import android.content.Context
import be.dimsumfamily.podcast.data.AppDatabase
import be.dimsumfamily.podcast.data.PodcastRepository

/** Process-wide singletons (lightweight manual DI). */
class PodcastApp : Application() {

    private val database by lazy { AppDatabase.get(this) }

    val repository by lazy {
        PodcastRepository(database.podcastDao(), database.episodeDao())
    }
}

/** Convenience accessor for the shared repository from any Context. */
val Context.podcastRepository: PodcastRepository
    get() = (applicationContext as PodcastApp).repository
