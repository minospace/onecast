package be.dimsumfamily.onecast.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Downloads an RSS feed and parses it into a [ParsedFeed]. */
class FeedFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val parser: RssParser = RssParser(),
) {
    suspend fun fetch(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "OnecastApp/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Feed request failed: HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: throw IOException("Empty feed response")
            parser.parse(stream)
        }
    }
}
