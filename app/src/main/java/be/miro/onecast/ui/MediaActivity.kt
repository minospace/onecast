package be.miro.onecast.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import be.miro.onecast.appSettings
import be.miro.onecast.data.AppSettings
import be.miro.onecast.data.PodcastRepository
import be.miro.onecast.playback.PlayerConnection
import be.miro.onecast.podcastRepository

/** Base activity that owns a lifecycle-bound [PlayerConnection] and exposes the repository. */
abstract class MediaActivity : AppCompatActivity() {

    protected lateinit var playerConnection: PlayerConnection
        private set

    protected val repository: PodcastRepository get() = podcastRepository

    protected val settings: AppSettings get() = appSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerConnection = PlayerConnection(this, lifecycle)
    }
}
