package be.dimsumfamily.onecast.feed

import android.util.Xml
import be.dimsumfamily.onecast.data.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Minimal RSS 2.0 / iTunes podcast feed parser built on the platform pull parser.
 * Namespace processing is left off so prefixed tags arrive verbatim (e.g. "itunes:duration").
 */
class RssParser {

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channelTitle: String? = null
        var channelAuthor: String? = null
        var channelDescription: String? = null
        var channelImage: String? = null

        val episodes = mutableListOf<ParsedEpisode>()

        var insideItem = false
        var itTitle: String? = null
        var itGuid: String? = null
        var itDesc: String? = null
        var itAudio: String? = null
        var itPubDate = 0L
        var itDuration = 0L
        var itImage: String? = null
        var itChaptersUrl: String? = null
        val itChapters = mutableListOf<Chapter>()

        var text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name: String? = parser.name
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when {
                        name.equals("item", true) -> {
                            insideItem = true
                            itTitle = null; itGuid = null; itDesc = null; itAudio = null
                            itPubDate = 0L; itDuration = 0L; itImage = null
                            itChaptersUrl = null; itChapters.clear()
                        }
                        name.equals("enclosure", true) && insideItem -> {
                            val type = parser.getAttributeValue(null, "type")
                            val url = parser.getAttributeValue(null, "url")
                            if (url != null && itAudio == null &&
                                (type == null || type.startsWith("audio", true))
                            ) {
                                itAudio = url
                            }
                        }
                        name.equals("itunes:image", true) -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) {
                                if (insideItem) itImage = href
                                else if (channelImage == null) channelImage = href
                            }
                        }
                        // Podcasting 2.0: a link to an external JSON chapters file.
                        name.equals("podcast:chapters", true) && insideItem -> {
                            val url = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type")
                            if (url != null && (type == null || type.contains("json", true))) {
                                itChaptersUrl = url
                            }
                        }
                        // Podlove Simple Chapters: inline, self-closing chapter elements.
                        name.equals("psc:chapter", true) && insideItem -> {
                            val start = parser.getAttributeValue(null, "start")
                            val chapterTitle = parser.getAttributeValue(null, "title")
                            if (start != null && !chapterTitle.isNullOrBlank()) {
                                itChapters.add(
                                    Chapter(
                                        startMs = parseTimecode(start),
                                        title = chapterTitle.trim(),
                                        imageUrl = parser.getAttributeValue(null, "image"),
                                        url = parser.getAttributeValue(null, "href"),
                                    ),
                                )
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> text.append(parser.text)

                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    if (insideItem) {
                        when {
                            name.equals("item", true) -> {
                                val audio = itAudio
                                if (audio != null) {
                                    episodes.add(
                                        ParsedEpisode(
                                            guid = itGuid ?: audio,
                                            title = itTitle ?: "(untitled)",
                                            description = itDesc,
                                            audioUrl = audio,
                                            pubDate = itPubDate,
                                            durationMs = itDuration,
                                            imageUrl = itImage,
                                            chapters = itChapters.sortedBy { it.startMs },
                                            chaptersUrl = itChaptersUrl,
                                        ),
                                    )
                                }
                                insideItem = false
                            }
                            name.equals("title", true) -> if (value.isNotBlank()) itTitle = value
                            name.equals("guid", true) -> if (value.isNotBlank()) itGuid = value
                            name.equals("pubDate", true) -> itPubDate = parseDate(value)
                            name.equals("itunes:duration", true) -> itDuration = parseDuration(value)
                            name.equals("content:encoded", true) -> if (value.isNotBlank()) itDesc = value
                            name.equals("description", true) -> if (itDesc == null && value.isNotBlank()) itDesc = value
                            name.equals("itunes:summary", true) -> if (itDesc == null && value.isNotBlank()) itDesc = value
                        }
                    } else {
                        when {
                            name.equals("title", true) -> if (channelTitle == null && value.isNotBlank()) channelTitle = value
                            name.equals("itunes:author", true) -> if (channelAuthor == null && value.isNotBlank()) channelAuthor = value
                            name.equals("description", true) -> if (channelDescription == null && value.isNotBlank()) channelDescription = value
                            name.equals("itunes:summary", true) -> if (channelDescription == null && value.isNotBlank()) channelDescription = value
                            // <image><url>…</url></image> at channel level
                            name.equals("url", true) -> if (channelImage == null && value.isNotBlank()) channelImage = value
                        }
                    }
                }
            }
            event = parser.next()
        }

        return ParsedFeed(channelTitle, channelAuthor, channelDescription, channelImage, episodes)
    }

    private fun parseDate(value: String): Long {
        if (value.isBlank()) return 0
        for (pattern in DATE_PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value)?.time ?: continue
            } catch (_: ParseException) {
                // try next pattern
            }
        }
        return 0
    }

    private fun parseDuration(value: String): Long {
        val v = value.trim()
        if (v.isBlank()) return 0
        return try {
            if (v.contains(":")) {
                val parts = v.split(":").map { it.trim().toLong() }
                val seconds = when (parts.size) {
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    2 -> parts[0] * 60 + parts[1]
                    1 -> parts[0]
                    else -> 0L
                }
                seconds * 1000
            } else {
                (v.toDouble() * 1000).toLong()
            }
        } catch (_: NumberFormatException) {
            0
        }
    }

    /** PSC start codes: "HH:MM:SS.mmm", "MM:SS", or plain (fractional) seconds → ms. */
    private fun parseTimecode(value: String): Long {
        val v = value.trim()
        if (v.isBlank()) return 0
        return try {
            var seconds = 0.0
            for (part in v.split(":")) seconds = seconds * 60 + part.toDouble()
            (seconds * 1000).toLong()
        } catch (_: NumberFormatException) {
            0
        }
    }

    private companion object {
        val DATE_PATTERNS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
        )
    }
}
