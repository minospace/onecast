package be.dimsumfamily.podcast.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import be.dimsumfamily.podcast.data.PodcastRepository
import be.dimsumfamily.podcast.playback.PlayerConnection
import be.dimsumfamily.podcast.podcastRepository

/** Base activity that owns a lifecycle-bound [PlayerConnection] and exposes the repository. */
abstract class MediaActivity : AppCompatActivity() {

    protected lateinit var playerConnection: PlayerConnection
        private set

    protected val repository: PodcastRepository get() = podcastRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerConnection = PlayerConnection(this, lifecycle)
    }
}
