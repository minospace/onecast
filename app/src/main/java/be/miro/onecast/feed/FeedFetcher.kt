package be.miro.onecast.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Downloads an RSS feed and parses it into a [ParsedFeed]. */
class FeedFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val parser: RssParser = RssParser(),
) {
    suspend fun fetch(feedUrl: String): ParsedFeed =
        fetchIfChanged(feedUrl, alwaysParse = true)!!

    /**
     * Like [fetch], but returns null when the server says the feed hasn't changed since the copy in
     * the HTTP cache (a `304 Not Modified` to the conditional request OkHttp sends on our behalf).
     * Most feeds are unchanged on most refreshes, and many are megabytes of XML, so this skips the
     * download *and* the parse and database pass. Feeds whose server sends no validators simply
     * come back in full every time, as before.
     */
    suspend fun fetchIfChanged(feedUrl: String, alwaysParse: Boolean = false): ParsedFeed? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "OnecastApp/1.0 (Android)")
                // Always ask the server (never trust a max-age), but let it answer "unchanged".
                .cacheControl(CacheControl.Builder().noCache().build())
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Feed request failed: HTTP ${response.code}")
                if (!alwaysParse && response.networkResponse?.code == 304) return@use null
                val stream = response.body?.byteStream() ?: throw IOException("Empty feed response")
                parser.parse(stream)
            }
        }
}
