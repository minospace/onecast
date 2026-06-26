package be.dimsumfamily.podcast.feed

import be.dimsumfamily.podcast.data.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches a Podcasting 2.0 JSON chapters file. Format (podcastindex chapters spec):
 * `{ "chapters": [ { "startTime": 0, "title": "…", "img": "…", "url": "…", "toc": true } ] }`.
 * `startTime` is in (fractional) seconds; a chapter with `"toc": false` is hidden.
 */
class ChaptersClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetch(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PodcastApp/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parse(body)
        }
    }

    private fun parse(body: String): List<Chapter> {
        val array = JSONObject(body).optJSONArray("chapters") ?: return emptyList()
        val chapters = mutableListOf<Chapter>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.has("toc") && !obj.optBoolean("toc", true)) continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            chapters.add(
                Chapter(
                    startMs = (obj.optDouble("startTime", 0.0) * 1000).toLong(),
                    title = title,
                    imageUrl = obj.optString("img").ifBlank { null },
                    url = obj.optString("url").ifBlank { null },
                ),
            )
        }
        return chapters.sortedBy { it.startMs }
    }
}
