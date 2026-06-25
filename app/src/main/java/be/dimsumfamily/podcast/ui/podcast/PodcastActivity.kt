package be.dimsumfamily.podcast.ui.podcast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import be.dimsumfamily.podcast.R
import be.dimsumfamily.podcast.data.Episode
import be.dimsumfamily.podcast.data.Podcast
import be.dimsumfamily.podcast.databinding.ActivityPodcastBinding
import be.dimsumfamily.podcast.playback.MediaItems
import be.dimsumfamily.podcast.ui.MediaActivity
import be.dimsumfamily.podcast.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class PodcastActivity : MediaActivity() {

    private lateinit var binding: ActivityPodcastBinding
    private lateinit var adapter: EpisodeAdapter

    private var podcastId = 0L
    private var podcast: Podcast? = null
    private var episodes: List<Episode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        podcastId = intent.getLongExtra(EXTRA_PODCAST_ID, 0L)
        if (podcastId == 0L) {
            finish()
            return
        }

        binding = ActivityPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarLayout.setNavigationButtonAsBack()

        adapter = EpisodeAdapter(onPlay = ::play, onTogglePlayed = ::togglePlayed)
        binding.episodeList.layoutManager = LinearLayoutManager(this)
        binding.episodeList.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.miniPlayer.bind(playerConnection) { openPlayer() }
        playerConnection.onUpdate = {
            binding.miniPlayer.refresh(playerConnection)
            adapter.setCurrentEpisode(MediaItems.episodeId(playerConnection.controller?.currentMediaItem))
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.observePodcast(podcastId).collect { p ->
                        podcast = p
                        p?.let { binding.toolbarLayout.setTitle(it.title) }
                        adapter.submit(podcast, episodes)
                    }
                }
                launch {
                    repository.observeEpisodes(podcastId).collect { list ->
                        episodes = list
                        adapter.submit(podcast, episodes)
                    }
                }
            }
        }
    }

    /** Open the full player, growing the artwork out of the mini-player as a shared element. */
    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, binding.miniPlayer.artView, "player_art",
        )
        startActivity(intent, options.toBundle())
    }

    private fun play(episode: Episode) {
        val item = MediaItems.fromEpisode(episode, podcast)
        val startAt = if (episode.isPlayed) 0L else episode.positionMs
        playerConnection.playEpisode(item, startAt)
    }

    private fun togglePlayed(episode: Episode) {
        lifecycleScope.launch { repository.setPlayed(episode.id, !episode.isPlayed) }
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                repository.refresh(podcastId)
            } catch (e: Exception) {
                toast("Couldn't refresh: ${e.message}")
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.podcast, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (onMenuItem(item.itemId)) true else super.onOptionsItemSelected(item)

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.action_refresh -> {
            binding.swipeRefresh.isRefreshing = true
            refresh()
            true
        }
        R.id.action_mark_all_played -> {
            confirmMarkAll(played = true)
            true
        }
        R.id.action_mark_all_unplayed -> {
            confirmMarkAll(played = false)
            true
        }
        R.id.action_unsubscribe -> {
            confirmUnsubscribe()
            true
        }
        else -> false
    }

    private fun confirmMarkAll(played: Boolean) {
        if (episodes.isEmpty()) return
        val verb = if (played) "played" else "unplayed"
        AlertDialog.Builder(this)
            .setTitle(if (played) "Mark all as played" else "Mark all as unplayed")
            .setMessage("Mark all ${episodes.size} episodes as $verb?")
            .setPositiveButton(if (played) "Mark played" else "Mark unplayed") { _, _ ->
                lifecycleScope.launch {
                    repository.setAllPlayed(podcastId, played)
                    toast("All episodes marked as $verb")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUnsubscribe() {
        AlertDialog.Builder(this)
            .setTitle("Unsubscribe")
            .setMessage("Remove \"${podcast?.title ?: "this podcast"}\" and its episodes?")
            .setPositiveButton("Unsubscribe") { _, _ ->
                lifecycleScope.launch {
                    repository.unsubscribe(podcastId)
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_PODCAST_ID = "podcast_id"

        fun start(context: Context, podcastId: Long) {
            context.startActivity(
                Intent(context, PodcastActivity::class.java)
                    .putExtra(EXTRA_PODCAST_ID, podcastId),
            )
        }
    }
}
