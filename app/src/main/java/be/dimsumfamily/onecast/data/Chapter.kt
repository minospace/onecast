package be.dimsumfamily.onecast.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single chapter marker within an episode. Sourced from inline Podlove Simple
 * Chapters (`psc:chapter`) or a Podcasting 2.0 JSON chapters file.
 */
data class Chapter(
    /** Chapter start offset from the beginning of the episode, in milliseconds. */
    val startMs: Long,
    val title: String,
    /** Optional per-chapter artwork. */
    val imageUrl: String? = null,
    /** Optional related link for the chapter. */
    val url: String? = null,
)

/** JSON (de)serialisation for the [Chapter] list persisted on an [Episode]. */
object ChapterJson {

    fun encode(chapters: List<Chapter>): String {
        val array = JSONArray()
        for (c in chapters) {
            array.put(
                JSONObject().apply {
                    put("startMs", c.startMs)
                    put("title", c.title)
                    c.imageUrl?.let { put("imageUrl", it) }
                    c.url?.let { put("url", it) }
                },
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<Chapter> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Chapter(
                    startMs = obj.optLong("startMs"),
                    title = obj.optString("title"),
                    imageUrl = obj.optString("imageUrl").ifBlank { null },
                    url = obj.optString("url").ifBlank { null },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Index of the chapter active at [positionMs], or -1 if before the first chapter. */
fun List<Chapter>.indexAt(positionMs: Long): Int {
    var index = -1
    for (i in indices) {
        if (this[i].startMs <= positionMs) index = i else break
    }
    return index
}
