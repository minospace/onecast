package be.miro.onecast.ui.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import be.miro.onecast.R
import be.miro.onecast.data.Episode
import be.miro.onecast.databinding.ActivityQueueBinding
import be.miro.onecast.playback.MediaItems
import be.miro.onecast.ui.MediaActivity
import be.miro.onecast.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/** The "Up Next" screen: the queue of episodes that autoplay after the current one. */
class QueueActivity : MediaActivity() {

    private lateinit var binding: ActivityQueueBinding
    private lateinit var adapter: QueueAdapter
    private var queueEmpty = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyAmoledBackground(binding.toolbarLayout)

        binding.toolbarLayout.setNavigationButtonAsBack()

        adapter = QueueAdapter(onPlay = ::play, onRemove = ::remove)
        binding.queueList.layoutManager = LinearLayoutManager(this)
        binding.queueList.adapter = adapter

        binding.miniPlayer.bind(playerConnection) { openPlayer() }
        playerConnection.onUpdate = { binding.miniPlayer.refresh(playerConnection) }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeQueue().collect { queue ->
                    adapter.submitList(queue)
                    queueEmpty = queue.isEmpty()
                    binding.emptyView.visibility = if (queueEmpty) View.VISIBLE else View.GONE
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun play(episode: Episode) {
        lifecycleScope.launch {
            val podcast = repository.getPodcast(episode.podcastId)
            val startAt = if (episode.isPlayed) 0L else episode.positionMs
            playerConnection.loadEpisode(MediaItems.fromEpisode(episode, podcast), startAt)
            repository.onEpisodeStarted(episode.id, settings.autoQueueNewer)
        }
    }

    private fun remove(episode: Episode) {
        lifecycleScope.launch { repository.removeFromQueue(episode.id) }
    }

    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, binding.miniPlayer.artView, "player_art",
        )
        startActivity(intent, options.toBundle())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.queue, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_queue)?.isVisible = !queueEmpty
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear_queue -> {
            confirmClear()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.queue_clear)
            .setMessage(R.string.queue_clear_message)
            .setPositiveButton(R.string.queue_clear) { _, _ ->
                lifecycleScope.launch { repository.clearQueue() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, QueueActivity::class.java))
        }
    }
}
