package be.dimsumfamily.podcast.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Lifecycle-aware bridge to the [PlaybackService] via a Media3 [MediaController].
 * Connects on START, disconnects on STOP, and calls [onUpdate] on every player
 * event plus a steady progress tick so the UI can refresh the seek bar.
 */
class PlayerConnection(
    private val context: Context,
    lifecycle: Lifecycle,
) : DefaultLifecycleObserver {

    private var future: ListenableFuture<MediaController>? = null

    var controller: MediaController? = null
        private set

    /** Called on player state changes and ~2x/second while connected. */
    var onUpdate: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            onUpdate?.invoke()
            handler.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            onUpdate?.invoke()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            {
                controller = runCatching { f.get() }.getOrNull()?.also {
                    it.addListener(playerListener)
                }
                onUpdate?.invoke()
                handler.post(ticker)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacks(ticker)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    fun loadEpisode(item: MediaItem, startPositionMs: Long) {
        val c = controller ?: return
        c.setMediaItem(item, startPositionMs.coerceAtLeast(0))
        c.prepare()
    }

    /** True if the controller is currently playing the given episode id. */
    fun isCurrent(episodeId: Long): Boolean =
        controller?.currentMediaItem?.mediaId == episodeId.toString()

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekBack() = controller?.seekBack() ?: Unit
    fun seekForward() = controller?.seekForward() ?: Unit
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit
    fun setSpeed(speed: Float) = controller?.setPlaybackSpeed(speed) ?: Unit

    private companion object {
        const val PROGRESS_TICK_MS = 500L
    }
}
