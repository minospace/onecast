package be.miro.onecast.ui.downloads

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import be.miro.onecast.R
import be.miro.onecast.data.EpisodeWithPodcast
import be.miro.onecast.databinding.ActivityDownloadsBinding
import be.miro.onecast.download.DownloadTask
import be.miro.onecast.ui.Format
import be.miro.onecast.ui.GroupedListDecoration
import be.miro.onecast.ui.MediaActivity
import be.miro.onecast.ui.player.PlayerActivity
import be.miro.onecast.ui.shareDownloadedEpisode
import be.miro.onecast.ui.showDestructive
import kotlinx.coroutines.launch

/** Every downloaded episode, plus whatever is downloading right now. */
class DownloadsActivity : MediaActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadsAdapter

    private var active: List<DownloadTask> = emptyList()
    private var finished: List<EpisodeWithPodcast> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarLayout.setNavigationButtonAsBack()

        adapter = DownloadsAdapter(
            onPlay = ::play,
            onDelete = ::confirmDelete,
            onCancel = { downloads.cancel(it.episodeId) },
            // Retry what the user originally asked for, video choice included.
            onRetry = { downloads.enqueue(it.episodeId, it.includeVideo) },
            onShare = { shareDownloadedEpisode(this, it.episode) },
        )
        binding.downloadList.layoutManager = LinearLayoutManager(this)
        binding.downloadList.adapter = adapter
        // Downloads in flight and finished downloads are two groups. Hairlines start past the
        // artwork: row padding + 52dp art + 16dp gap.
        val density = resources.displayMetrics.density
        val dividerInset = resources.getDimensionPixelSize(R.dimen.row_padding_horizontal) + ((52 + 16) * density).toInt()
        binding.downloadList.addItemDecoration(
            GroupedListDecoration(this, dividerInset) { position ->
                adapter.currentList.getOrNull(position)?.javaClass
            },
        )

        binding.miniPlayer.bind(playerConnection) { openPlayer() }
        playerConnection.onUpdate = { binding.miniPlayer.refresh(playerConnection) }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.observeDownloads().collect {
                        finished = it
                        render()
                    }
                }
                launch {
                    downloads.tasks.collect {
                        active = it
                        render()
                    }
                }
            }
        }
    }

    private fun render() {
        val rows = active.map { DownloadRow.Active(it) } + finished.map { DownloadRow.Finished(it) }
        adapter.submitList(rows)
        binding.emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        // render() runs on every download progress tick; rebuilding the menu each time is wasted
        // work and makes an open overflow menu flicker. Only when the bulk action's visibility flips.
        val canDeleteAll = finished.any { !it.podcastIsLocal }
        if (canDeleteAll != menuCanDeleteAll) {
            menuCanDeleteAll = canDeleteAll
            invalidateOptionsMenu()
        }
    }

    private var menuCanDeleteAll: Boolean? = null

    private fun play(item: EpisodeWithPodcast) {
        lifecycleScope.launch {
            val episode = item.episode
            val podcast = repository.getPodcast(episode.podcastId)
            val startAt = if (episode.isPlayed) 0L else episode.positionMs
            playerConnection.loadEpisode(episode, podcast, startAt)
            repository.onEpisodeStarted(episode.id, settings.autoQueueNewer)
        }
    }

    /**
     * A real download can be deleted and re-fetched later; a local episode's file is its only
     * copy, so "deleting" it here is the same permanent removal as on the podcast screen.
     */
    private fun confirmDelete(item: EpisodeWithPodcast) {
        if (item.podcastIsLocal) {
            confirmRemoveLocalEpisode(item)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.downloads_delete_title)
            .setMessage(getString(R.string.downloads_delete_message, item.episode.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteDownload(item.episode.id)
                    toast(getString(R.string.downloads_deleted))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun confirmRemoveLocalEpisode(item: EpisodeWithPodcast) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_episode_remove_title, item.episode.title))
            .setMessage(R.string.custom_episode_remove_message)
            .setPositiveButton(R.string.remove) { _, _ ->
                lifecycleScope.launch {
                    // The file goes with it, so don't leave the player pointing at it.
                    if (playerConnection.isCurrent(item.episode.id)) playerConnection.clear()
                    repository.deleteLocalEpisode(item.episode.id)
                    toast(getString(R.string.custom_episode_removed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.sourceBounds = binding.miniPlayer.artScreenBounds()
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, binding.miniPlayer.artView, "player_art",
        )
        startActivity(intent, options.toBundle())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.downloads, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Local episodes aren't part of the bulk action (see confirmDeleteAll) — no point showing
        // it when they're all that's downloaded.
        menu.findItem(R.id.action_delete_all_downloads)?.isVisible = finished.any { !it.podcastIsLocal }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_delete_all_downloads -> {
            confirmDeleteAll()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Only ever touches real downloads (see [PodcastRepository.deleteAllDownloads]). */
    private fun confirmDeleteAll() {
        val eligible = finished.filterNot { it.podcastIsLocal }
        val count = eligible.size
        val bytes = eligible.sumOf { it.episode.downloadSizeBytes }
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.downloads_delete_all_title, count, count))
            .setMessage(getString(R.string.downloads_delete_all_message, Format.fileSize(bytes)))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteAllDownloads()
                    toast(getString(R.string.downloads_deleted_all))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DownloadsActivity::class.java))
        }
    }
}
