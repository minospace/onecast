package be.dimsumfamily.onecast.feed

import be.dimsumfamily.onecast.data.Chapter

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
    /** Inline Podlove Simple Chapters, if present. */
    val chapters: List<Chapter> = emptyList(),
    /** Podcasting 2.0 JSON chapters URL, if present. */
    val chaptersUrl: String? = null,
)
