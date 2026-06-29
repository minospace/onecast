package be.miro.onecast.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import be.miro.onecast.OnecastApp
import be.miro.onecast.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background audio playback via Media3. Media3 supplies the media notification and
 * lock-screen controls automatically; this service adds podcast-specific behaviour:
 * persisting the resume position and auto-marking an episode played when it finishes.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val repository get() = (application as OnecastApp).repository
    private val settings get() = (application as OnecastApp).settings

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Podcast audio URLs almost always redirect through tracking/CDN hosts,
        // often across http<->https. Allow that and send a User-Agent CDNs accept.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("OnecastApp/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory),
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // The user-configured skip amounts drive the notification / lock-screen seek buttons.
            // The in-app controls seek by the latest value directly (see PlayerConnection), so they
            // stay in sync even while this (long-lived) service keeps the increments it was built with.
            .setSeekBackIncrementMs(settings.rewindSeconds * 1000L)
            .setSeekForwardIncrementMs(settings.forwardSeconds * 1000L)
            .build()

        player.addListener(playerListener)
        // Wrap so a finished episode replays from the start when the user hits play again; a raw
        // Media3 player ignores play() in STATE_ENDED, which otherwise leaves every play button
        // (full player, mini-player, widget, notification) dead once an episode reaches the end.
        session = MediaSession.Builder(this, ReplayWhenEndedPlayer(player)).build()
        restoreLastEpisode(player)
        startProgressLoop()
    }

    /**
     * Reloads the episode that was loaded when the service was last torn down, paused at its saved
     * position. Because this service only runs in the foreground while actually playing, an episode
     * that was picked but never played is otherwise lost as soon as the app is backgrounded.
     */
    private fun restoreLastEpisode(player: Player) {
        val episodeId = settings.lastEpisodeId
        if (episodeId < 0 || player.currentMediaItem != null) return
        scope.launch {
            val episode = repository.getEpisode(episodeId) ?: return@launch
            // The user may have started something else while we were loading from the DB.
            if (player.currentMediaItem != null) return@launch
            val podcast = repository.getPodcast(episode.podcastId)
            val startAt = if (episode.isPlayed) 0L else episode.positionMs
            player.setMediaItem(MediaItems.fromEpisode(episode, podcast), startAt.coerceAtLeast(0))
            player.prepare()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        session?.let { saveCurrentPosition(it.player) }
        session?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Remember what's loaded so it survives the service being killed while paused.
            settings.lastEpisodeId = MediaItems.episodeId(mediaItem) ?: -1L
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            session?.player?.let { saveCurrentPosition(it); pushWidgetState(it) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            session?.player?.let { pushWidgetState(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val episodeId = MediaItems.episodeId(session?.player?.currentMediaItem) ?: return
            scope.launch { repository.setPlayed(episodeId, true) }
        }
    }

    /** Mirrors the player's current episode + playing state for the home-screen widget. */
    private fun pushWidgetState(player: Player) {
        val item = player.currentMediaItem ?: return
        val episodeId = MediaItems.episodeId(item) ?: return
        val metadata = player.mediaMetadata
        WidgetState.update(
            context = applicationContext,
            episodeId = episodeId,
            title = metadata.title?.toString() ?: "",
            podcastTitle = metadata.artist?.toString() ?: "",
            artworkUrl = metadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
        )
        WidgetState.notifyWidgets(applicationContext)
    }

    private fun startProgressLoop() {
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                session?.player?.let {
                    if (it.isPlaying) {
                        saveCurrentPosition(it)
                        // Keep the home-screen widget's progress bar advancing while playing.
                        pushWidgetState(it)
                    }
                }
            }
        }
    }

    /** Reads playback position on the player thread, then persists off the main thread. */
    private fun saveCurrentPosition(player: Player) {
        val item: MediaItem = player.currentMediaItem ?: return
        val episodeId = MediaItems.episodeId(item) ?: return
        // A finished episode should resume from the start, not its very end. setPlayed (on
        // STATE_ENDED) clears this too, but the two writes race, so make this one authoritative.
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        scope.launch {
            repository.savePosition(episodeId, position)
            if (duration > 0) repository.saveDurationIfUnknown(episodeId, duration)
        }
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}

/**
 * Makes "play" replay a finished episode instead of doing nothing. A Media3 [Player] ignores
 * `play()` / `setPlayWhenReady(true)` while in [Player.STATE_ENDED], so seek back to the start
 * first. Both entry points are overridden because the session, notification and app controls
 * reach the player through different ones.
 */
private class ReplayWhenEndedPlayer(player: Player) : ForwardingPlayer(player) {
    override fun play() {
        restartIfEnded()
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) restartIfEnded()
        super.setPlayWhenReady(playWhenReady)
    }

    private fun restartIfEnded() {
        if (playbackState == Player.STATE_ENDED && mediaItemCount > 0) seekToDefaultPosition()
    }
}
