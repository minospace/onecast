package be.miro.onecast.feed

/** A podcast returned by the iTunes Search API. */
data class PodcastSearchResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val artworkUrl: String?,
)
