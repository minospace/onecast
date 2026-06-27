package be.dimsumfamily.onecast.data

import be.dimsumfamily.onecast.feed.ChaptersClient
import be.dimsumfamily.onecast.feed.FeedFetcher
import be.dimsumfamily.onecast.feed.ItunesSearchClient
import be.dimsumfamily.onecast.feed.ParsedFeed
import be.dimsumfamily.onecast.feed.PodcastSearchResult
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
    private val chaptersClient = ChaptersClient(httpClient)

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

    /** Refresh subscribed feeds not refreshed within [maxAgeMs] (default 30 min); broken feeds are skipped. */
    suspend fun refreshStalePodcasts(maxAgeMs: Long = 30 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        for (podcast in podcastDao.getAll()) {
            if (now - podcast.lastRefreshed < maxAgeMs) continue
            try {
                refresh(podcast.id)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun unsubscribe(podcastId: Long) = podcastDao.deleteById(podcastId)

    suspend fun setPlayed(episodeId: Long, played: Boolean) = episodeDao.setPlayed(episodeId, played)
    suspend fun setAllPlayed(podcastId: Long, played: Boolean) = episodeDao.setAllPlayed(podcastId, played)
    suspend fun savePosition(episodeId: Long, positionMs: Long) = episodeDao.updatePosition(episodeId, positionMs)
    suspend fun saveDurationIfUnknown(episodeId: Long, durationMs: Long) =
        episodeDao.updateDurationIfUnknown(episodeId, durationMs)

    /**
     * Returns the episode's chapters, lazily fetching and caching a Podcasting 2.0
     * JSON chapters file on first request when no inline chapters were in the feed.
     */
    suspend fun ensureChapters(episodeId: Long): List<Chapter> {
        val episode = episodeDao.getById(episodeId) ?: return emptyList()
        if (episode.chapters.isNotEmpty()) return episode.chapters
        val url = episode.chaptersUrl ?: return emptyList()
        return try {
            chaptersClient.fetch(url).also {
                if (it.isNotEmpty()) episodeDao.updateChapters(episodeId, it)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

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
                imageUrl = e.imageUrl,
                chapters = e.chapters,
                chaptersUrl = e.chaptersUrl,
            )
        }
        // IGNORE conflicts on (podcastId, guid) → only genuinely new episodes are added.
        episodeDao.insertAll(episodes)
        // Episodes that already existed (e.g. added before chapter/image support was introduced)
        // were skipped by the insert above; backfill that info from this parse.
        for (e in episodes) {
            if (e.chapters.isNotEmpty() || e.chaptersUrl != null) {
                episodeDao.backfillChapters(podcastId, e.guid, e.chapters, e.chaptersUrl)
            }
            if (e.imageUrl != null) {
                episodeDao.backfillImage(podcastId, e.guid, e.imageUrl)
            }
        }
    }
}
