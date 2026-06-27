package be.dimsumfamily.onecast.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import be.dimsumfamily.onecast.appSettings
import be.dimsumfamily.onecast.data.AppSettings
import be.dimsumfamily.onecast.data.PodcastRepository
import be.dimsumfamily.onecast.playback.PlayerConnection
import be.dimsumfamily.onecast.podcastRepository

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
