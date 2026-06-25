package be.dimsumfamily.podcast.feed

/** Plain result of parsing an RSS feed, independent of the database layer. */
data class ParsedFeed(
    val title: String?,
    val author: String?,
    val description: String?,
    val imageUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val pubDate: Long,
    val durationMs: Long,
    val imageUrl: String?,
)
