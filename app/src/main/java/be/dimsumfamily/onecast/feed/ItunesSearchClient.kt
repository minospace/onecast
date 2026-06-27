package be.dimsumfamily.onecast.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Searches podcasts through Apple's free iTunes Search API (no key required).
 * https://performance-partners.apple.com/search-api
 */
class ItunesSearchClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun search(term: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()

        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "25")
            .addQueryParameter("term", term)
            .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()

            buildList {
                for (i in 0 until results.length()) {
                    val o = results.optJSONObject(i) ?: continue
                    val feed = o.optString("feedUrl").takeIf { it.isNotBlank() } ?: continue
                    add(
                        PodcastSearchResult(
                            title = o.optString("collectionName").ifBlank { o.optString("trackName") },
                            author = o.optString("artistName").takeIf { it.isNotBlank() },
                            feedUrl = feed,
                            artworkUrl = o.optString("artworkUrl600").takeIf { it.isNotBlank() }
                                ?: o.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }
}
