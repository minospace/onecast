package be.dimsumfamily.podcast.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import be.dimsumfamily.podcast.data.Episode
import be.dimsumfamily.podcast.data.Podcast

/** Builds Media3 [MediaItem]s, carrying the episode id as the media id. */
object MediaItems {

    fun fromEpisode(episode: Episode, podcast: Podcast?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast?.title)
            .setArtworkUri(podcast?.artworkUrl?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id.toString())
            .setUri(episode.audioUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun episodeId(mediaItem: MediaItem?): Long? = mediaItem?.mediaId?.toLongOrNull()
}
