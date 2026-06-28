package be.miro.onecast.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import be.miro.onecast.data.Episode
import be.miro.onecast.data.Podcast

/** Builds Media3 [MediaItem]s, carrying the episode id as the media id. */
object MediaItems {

    fun fromEpisode(episode: Episode, podcast: Podcast?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast?.title)
            .setArtworkUri((episode.imageUrl ?: podcast?.artworkUrl)?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id.toString())
            .setUri(episode.audioUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun episodeId(mediaItem: MediaItem?): Long? = mediaItem?.mediaId?.toLongOrNull()
}
