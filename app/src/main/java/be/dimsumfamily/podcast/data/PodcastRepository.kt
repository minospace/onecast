package be.dimsumfamily.podcast.data

import be.dimsumfamily.podcast.feed.FeedFetcher
import be.dimsumfamily.podcast.feed.ItunesSearchClient
import be.dimsumfamily.podcast.feed.ParsedFeed
import be.dimsumfamily.podcast.feed.PodcastSearchResult
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/** Single source of truth: wraps the DAOs and the network feed/search clients. */
class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val feedFetcher = FeedFetcher(httpClient)
    private val itunes = ItunesSearchClient(httpClient)

    fun observePodcasts(): Flow<List<Podcast>> = podcastDao.observeAll()
    fun observePodcast(id: Long): Flow<Podcast?> = podcastDao.observeById(id)
    fun observeEpisodes(podcastId: Long): Flow<List<Episode>> = episodeDao.observeForPodcast(podcastId)
    fun observeEpisode(id: Long): Flow<Episode?> = episodeDao.observeById(id)

    suspend fun getPodcast(id: Long): Podcast? = podcastDao.getById(id)
    suspend fun getEpisode(id: Long): Episode? = episodeDao.getById(id)

    suspend fun search(term: String): List<PodcastSearchResult> = itunes.search(term)

    /** Subscribe to a feed URL (idempotent). Returns the podcast id. */
    suspend fun subscribe(feedUrl: String): Long {
        val parsed = feedFetcher.fetch(feedUrl)
        val existing = podcastDao.getByFeedUrl(feedUrl)
        val podcastId: Long = if (existing == null) {
            val inserted = podcastDao.insert(
                Podcast(
                    feedUrl = feedUrl,
                    title = parsed.title ?: feedUrl,
                    author = parsed.author,
                    description = parsed.description,
                    artworkUrl = parsed.imageUrl,
                    lastRefreshed = System.currentTimeMillis(),
                ),
            )
            if (inserted == -1L) podcastDao.getByFeedUrl(feedUrl)!!.id else inserted
        } else {
            updatePodcastFromFeed(existing, parsed)
            existing.id
        }
        insertNewEpisodes(podcastId, parsed)
        return podcastId
    }

    /** Re-fetch a subscribed feed and add any new episodes. */
    suspend fun refresh(podcastId: Long) {
        val podcast = podcastDao.getById(podcastId) ?: return
        val parsed = feedFetcher.fetch(podcast.feedUrl)
        updatePodcastFromFeed(podcast, parsed)
        insertNewEpisodes(podcastId, parsed)
    }

    suspend fun unsubscribe(podcastId: Long) = podcastDao.deleteById(podcastId)

    suspend fun setPlayed(episodeId: Long, played: Boolean) = episodeDao.setPlayed(episodeId, played)
    suspend fun setAllPlayed(podcastId: Long, played: Boolean) = episodeDao.setAllPlayed(podcastId, played)
    suspend fun savePosition(episodeId: Long, positionMs: Long) = episodeDao.updatePosition(episodeId, positionMs)
    suspend fun saveDurationIfUnknown(episodeId: Long, durationMs: Long) =
        episodeDao.updateDurationIfUnknown(episodeId, durationMs)

    private suspend fun updatePodcastFromFeed(existing: Podcast, parsed: ParsedFeed) {
        podcastDao.update(
            existing.copy(
                title = parsed.title ?: existing.title,
                author = parsed.author ?: existing.author,
                description = parsed.description ?: existing.description,
                artworkUrl = parsed.imageUrl ?: existing.artworkUrl,
                lastRefreshed = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun insertNewEpisodes(podcastId: Long, parsed: ParsedFeed) {
        val episodes = parsed.episodes.map { e ->
            Episode(
                podcastId = podcastId,
                guid = e.guid,
                title = e.title,
                description = e.description,
                audioUrl = e.audioUrl,
                pubDate = e.pubDate,
                durationMs = e.durationMs,
            )
        }
        // IGNORE conflicts on (podcastId, guid) → only genuinely new episodes are added.
        episodeDao.insertAll(episodes)
    }
}
