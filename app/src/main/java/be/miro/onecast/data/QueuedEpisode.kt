package be.miro.onecast.data

import androidx.room.Embedded

/**
 * A queued [Episode] joined with its podcast's title and artwork, so the Up Next list can render a
 * row (which podcast, fallback art) without a second lookup per episode.
 */
data class QueuedEpisode(
    @Embedded val episode: Episode,
    val podcastTitle: String,
    val podcastArtwork: String?,
)
