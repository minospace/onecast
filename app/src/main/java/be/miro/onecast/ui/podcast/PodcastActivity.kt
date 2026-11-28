package be.miro.onecast.ui.podcast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import be.miro.onecast.R
import be.miro.onecast.data.Episode
import be.miro.onecast.data.Podcast
import be.miro.onecast.databinding.ActivityPodcastBinding
import be.miro.onecast.download.DownloadState
import be.miro.onecast.playback.MediaItems
import be.miro.onecast.ui.GroupedListDecoration
import be.miro.onecast.ui.MediaActivity
import be.miro.onecast.ui.custom.CustomPodcastActivity
import be.miro.onecast.ui.downloads.DownloadsActivity
import be.miro.onecast.ui.player.PlayerActivity
import be.miro.onecast.ui.queue.QueueSheet
import be.miro.onecast.ui.shareDownloadedEpisode
import be.miro.onecast.ui.showDestructive
import be.miro.onecast.ui.showUndo
import kotlinx.coroutines.launch

class PodcastActivity : MediaActivity() {

    private lateinit var binding: ActivityPodcastBinding
    private lateinit var adapter: EpisodeAdapter

    private var podcastId = 0L
    private var podcast: Podcast? = null
    private var episodes: List<Episode> = emptyList()
    private var hidePlayed = false
    private var queuedIds: Set<Long> = emptySet()

    /** True for a podcast the user built themselves — no feed to refresh, files they can manage. */
    private var isLocal = false
    private var reverseQueue: Boolean? = null

    /**
     * Pick audio files to add to a user-created podcast. Multiple at a time, kept in pick order.
     * The files are copied into app storage, so the picker's short-lived read grant is enough.
     */
    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        toast(getString(R.string.custom_importing))
        // App-scoped, not lifecycleScope: copying large files takes a while, and backing out (or
        // rotating) mid-import would cancel it after the files were copied but before any episode
        // rows were written — leaving orphaned files and an empty podcast.
        val app = application as be.miro.onecast.OnecastApp
        val podcastId = podcastId
        app.appScope.launch {
            val added = app.repository.addLocalEpisodes(podcastId, uris)
            // The screen may be gone by now, so report through the application context.
            Toast.makeText(
                app,
                if (added == 0) {
                    app.getString(R.string.custom_episodes_none_added)
                } else {
                    app.resources.getQuantityString(R.plurals.custom_episodes_added, added, added)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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

        adapter = EpisodeAdapter(
            onPlay = ::play,
            onTogglePlayed = ::togglePlayed,
            onLongPress = ::showEpisodeMenu,
        )
        binding.episodeList.layoutManager = LinearLayoutManager(this)
        binding.episodeList.adapter = adapter
        // The episodes form one rounded group under the header.
        binding.episodeList.addItemDecoration(
            GroupedListDecoration(this, resources.getDimensionPixelSize(R.dimen.row_padding_horizontal)) {
                if (adapter.isEpisodeRow(it)) EPISODE_GROUP else null
            },
        )

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
                        // A local podcast has a different menu and no pull-to-refresh; both only
                        // need rebuilding when that actually changes, not on every row update.
                        if (p?.reverseQueue != reverseQueue) {
                            reverseQueue = p?.reverseQueue
                            invalidateOptionsMenu()
                        }
                        if (p != null && p.isLocal != isLocal) {
                            isLocal = p.isLocal
                            binding.swipeRefresh.isEnabled = !isLocal
                            invalidateOptionsMenu()
                        }
                        render()
                    }
                }
                launch {
                    repository.observeEpisodes(podcastId).collect { list ->
                        episodes = list
                        render()
                    }
                }
                launch {
                    settings.observeHidePlayedEpisodes().collect { hide ->
                        hidePlayed = hide
                        invalidateOptionsMenu()
                        render()
                    }
                }
                launch {
                    repository.observeQueueEpisodeIds().collect { ids ->
                        queuedIds = ids.toHashSet()
                    }
                }
                launch {
                    downloads.tasks.collect { tasks ->
                        adapter.setDownloadingIds(
                            tasks.filter { it.state != DownloadState.FAILED }
                                .mapTo(HashSet()) { it.episodeId },
                        )
                    }
                }
            }
        }
    }

    /** Push the (optionally filtered) episode list to the adapter. */
    private fun render() {
        // A feed reads newest first; a podcast the user assembled reads in the order they added
        // the files, which is what they picked.
        val ordered = if (isLocal) episodes.sortedBy { it.pubDate } else episodes
        val visible = if (hidePlayed) ordered.filter { !it.isPlayed } else ordered
        adapter.submit(podcast, visible)
        binding.emptyView.visibility =
            if (isLocal && episodes.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Open the full player, growing the artwork out of the mini-player as a shared element. */
    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.sourceBounds = binding.miniPlayer.artScreenBounds()
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, binding.miniPlayer.artView, "player_art",
        )
        startActivity(intent, options.toBundle())
    }

    private fun play(episode: Episode) {
        val startAt = if (episode.isPlayed) 0L else episode.positionMs
        playerConnection.loadEpisode(episode, podcast, startAt)
        // Starting an episode makes it "current", so drop it from the queue and (if enabled) line up
        // this podcast's newer unplayed episodes behind it.
        lifecycleScope.launch { repository.onEpisodeStarted(episode.id, settings.autoQueueNewer) }
    }

    private fun togglePlayed(episode: Episode) {
        val played = !episode.isPlayed
        lifecycleScope.launch {
            repository.setPlayed(episode.id, played)
            undoable(getString(if (played) R.string.marked_played else R.string.marked_unplayed)) {
                repository.restorePlayedState(episode.id, episode.isPlayed, episode.positionMs)
            }
        }
    }

    /** Confirm a change with an Undo action that runs [undo]. */
    private fun undoable(message: String, undo: suspend () -> Unit) {
        showUndo(binding.root, message, anchor = binding.miniPlayer) {
            lifecycleScope.launch { undo() }
        }
    }

    /** Long-press an episode: quick queue and download actions. */
    private fun showEpisodeMenu(episode: Episode, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_PLAY_NEXT, 0, R.string.queue_play_next)
        if (episode.id in queuedIds) {
            popup.menu.add(0, MENU_REMOVE, 1, R.string.queue_remove)
        } else {
            popup.menu.add(0, MENU_ADD, 1, R.string.queue_add)
        }
        when {
            // A local episode is already a file on the phone; there is nothing to download, and
            // removing it is the action that makes sense there instead.
            isLocal -> {
                if (episode.downloadPath != null) {
                    popup.menu.add(0, MENU_SHARE_FILE, 2, R.string.downloads_share)
                }
                popup.menu.add(0, MENU_REMOVE_EPISODE, 3, R.string.custom_episode_remove)
            }
            downloads.isPending(episode.id) ->
                popup.menu.add(0, MENU_CANCEL_DOWNLOAD, 2, R.string.download_cancel_download)
            episode.downloadPath != null -> {
                popup.menu.add(0, MENU_SHARE_FILE, 2, R.string.downloads_share)
                popup.menu.add(0, MENU_DELETE_DOWNLOAD, 3, R.string.downloads_delete)
            }
            else -> popup.menu.add(0, MENU_DOWNLOAD, 2, R.string.downloads_download)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PLAY_NEXT -> queueChange(episode, R.string.queue_added_next) { repository.playNext(it) }
                MENU_ADD -> queueChange(episode, R.string.queue_added) { repository.addToQueue(it) }
                MENU_REMOVE -> queueChange(episode, R.string.queue_removed) { repository.removeFromQueue(it) }
                MENU_DOWNLOAD -> {
                    startDownload(episode)
                    true
                }
                MENU_CANCEL_DOWNLOAD -> {
                    downloads.cancel(episode.id)
                    toast(getString(R.string.downloads_cancelled))
                    true
                }
                MENU_SHARE_FILE -> {
                    shareDownloadedEpisode(this, episode)
                    true
                }
                MENU_DELETE_DOWNLOAD -> actThenToast(episode, R.string.downloads_deleted) {
                    repository.deleteDownload(it)
                }
                MENU_REMOVE_EPISODE -> {
                    confirmRemoveEpisode(episode)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Download an episode, letting the user decide what to do about video first.
     *
     * When the feed publishes video as its own file alongside the audio, that's a straight choice
     * between the two. When the episode *is* a video file (the usual case — one enclosure carrying
     * both tracks) there's no audio-only version to fetch, so the choice is only whether to accept
     * a video-sized download at all.
     */
    private fun startDownload(episode: Episode) {
        val builder = AlertDialog.Builder(this)
        when {
            // Buttons rather than a list: a dialog shows either its message or a list of items,
            // never both, and the size warning is the whole point of asking.
            episode.hasSeparateVideoFile -> builder
                .setTitle(R.string.download_video_choice_title)
                .setMessage(R.string.download_video_choice_message)
                .setPositiveButton(R.string.download_video_choice_video) { _, _ ->
                    enqueueDownload(episode, includeVideo = true)
                }
                .setNegativeButton(R.string.download_video_choice_audio) { _, _ ->
                    enqueueDownload(episode, includeVideo = false)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
            episode.hasVideo -> builder
                .setTitle(R.string.download_video_only_title)
                .setMessage(R.string.download_video_only_message)
                .setPositiveButton(R.string.downloads_download) { _, _ ->
                    enqueueDownload(episode, includeVideo = true)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            else -> enqueueDownload(episode, includeVideo = false)
        }
    }

    private fun enqueueDownload(episode: Episode, includeVideo: Boolean) {
        downloads.enqueue(episode.id, includeVideo)
        toast(getString(R.string.downloads_started))
    }

    /** Change Up Next, then offer to undo it — putting the episode back exactly where it was. */
    private fun queueChange(
        episode: Episode,
        messageRes: Int,
        action: suspend (Long) -> Unit,
    ): Boolean {
        lifecycleScope.launch {
            val before = repository.queueSlot(episode.id)
            action(episode.id)
            undoable(getString(messageRes)) { repository.restoreQueueSlot(episode.id, before) }
        }
        return true
    }

    private fun actThenToast(
        episode: Episode,
        messageRes: Int,
        action: suspend (Long) -> Unit,
    ): Boolean {
        lifecycleScope.launch {
            action(episode.id)
            toast(getString(messageRes))
        }
        return true
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                repository.refresh(podcastId)
            } catch (e: Exception) {
                toast(getString(R.string.refresh_failed, e.message.orEmpty()))
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.podcast, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_hide_played)?.isChecked = hidePlayed
        menu.findItem(R.id.action_reverse_queue)?.isChecked = podcast?.reverseQueue == true
        // A user-created podcast has files to add and no feed behind it, so it swaps refresh and
        // unsubscribe for the actions that apply to something they own.
        menu.findItem(R.id.action_refresh)?.isVisible = !isLocal
        menu.findItem(R.id.action_unsubscribe)?.isVisible = !isLocal
        menu.findItem(R.id.action_add_episodes)?.isVisible = isLocal
        menu.findItem(R.id.action_edit_podcast)?.isVisible = isLocal
        menu.findItem(R.id.action_delete_podcast)?.isVisible = isLocal
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (onMenuItem(item.itemId)) true else super.onOptionsItemSelected(item)

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.action_refresh -> {
            binding.swipeRefresh.isRefreshing = true
            refresh()
            true
        }
        R.id.action_queue -> {
            QueueSheet.show(this)
            true
        }
        R.id.action_downloads -> {
            DownloadsActivity.start(this)
            true
        }
        R.id.action_hide_played -> {
            // Persisting flips the setting Flow, which updates the checkbox and re-renders.
            settings.hidePlayedEpisodes = !hidePlayed
            true
        }
        R.id.action_reverse_queue -> {
            // Persisting flips the podcast row, whose Flow updates the checkbox.
            val reverse = podcast?.reverseQueue != true
            lifecycleScope.launch {
                repository.setReverseQueue(podcastId, reverse)
                toast(getString(if (reverse) R.string.reverse_queue_on else R.string.reverse_queue_off))
            }
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
        R.id.action_add_episodes -> {
            pickAudio.launch(arrayOf("audio/*"))
            true
        }
        R.id.action_edit_podcast -> {
            startActivity(CustomPodcastActivity.editIntent(this, podcastId))
            true
        }
        R.id.action_delete_podcast -> {
            confirmDeleteLocalPodcast()
            true
        }
        else -> false
    }

    private fun confirmMarkAll(played: Boolean) {
        if (episodes.isEmpty()) return
        val count = episodes.size
        val builder = AlertDialog.Builder(this)
            .setTitle(
                resources.getQuantityString(
                    if (played) R.plurals.mark_all_played_title else R.plurals.mark_all_unplayed_title,
                    count,
                    count,
                ),
            )
        // Only marking played loses anything (the resume positions), so only it needs a warning.
        if (played) builder.setMessage(R.string.mark_all_played_message)
        builder
            .setPositiveButton(
                if (played) R.string.mark_all_played_confirm else R.string.mark_all_unplayed_confirm,
            ) { _, _ ->
                lifecycleScope.launch {
                    repository.setAllPlayed(podcastId, played)
                    toast(getString(if (played) R.string.marked_all_played else R.string.marked_all_unplayed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun podcastName(): String = podcast?.title ?: getString(R.string.this_podcast)

    private fun confirmUnsubscribe() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.unsubscribe_title, podcastName()))
            .setMessage(R.string.unsubscribe_message)
            .setPositiveButton(R.string.action_unsubscribe) { _, _ ->
                lifecycleScope.launch {
                    repository.unsubscribe(podcastId)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun confirmRemoveEpisode(episode: Episode) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_episode_remove_title, episode.title))
            .setMessage(R.string.custom_episode_remove_message)
            .setPositiveButton(R.string.remove) { _, _ ->
                lifecycleScope.launch {
                    // The file goes with the row, so don't leave the player pointing at it.
                    if (playerConnection.isCurrent(episode.id)) playerConnection.clear()
                    repository.deleteLocalEpisode(episode.id)
                    toast(getString(R.string.custom_episode_removed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun confirmDeleteLocalPodcast() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_delete_podcast_title, podcastName()))
            .setMessage(R.string.custom_delete_podcast_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    if (episodes.any { playerConnection.isCurrent(it.id) }) playerConnection.clear()
                    repository.unsubscribe(podcastId)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showDestructive()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_PODCAST_ID = "podcast_id"
        private const val MENU_PLAY_NEXT = 1
        private const val MENU_ADD = 2
        private const val MENU_REMOVE = 3
        private const val MENU_DOWNLOAD = 4
        private const val MENU_CANCEL_DOWNLOAD = 5
        private const val MENU_DELETE_DOWNLOAD = 6
        private const val MENU_REMOVE_EPISODE = 7
        private const val MENU_SHARE_FILE = 8
        private const val EPISODE_GROUP = "episodes"

        fun start(context: Context, podcastId: Long) {
            context.startActivity(
                Intent(context, PodcastActivity::class.java)
                    .putExtra(EXTRA_PODCAST_ID, podcastId),
            )
        }
    }
}
