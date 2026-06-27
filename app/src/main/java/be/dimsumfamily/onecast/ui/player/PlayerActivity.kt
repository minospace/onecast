package be.dimsumfamily.onecast.ui.player

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.Slide
import android.transition.TransitionSet
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.dimsumfamily.onecast.R
import be.dimsumfamily.onecast.data.Chapter
import be.dimsumfamily.onecast.data.indexAt
import be.dimsumfamily.onecast.databinding.ActivityPlayerBinding
import be.dimsumfamily.onecast.playback.MediaItems
import be.dimsumfamily.onecast.ui.Format
import be.dimsumfamily.onecast.ui.MediaActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.launch

/** Full-screen now-playing UI driven entirely by the shared MediaController. */
class PlayerActivity : MediaActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var userSeeking = false
    private var enterTransitionStarted = false
    private var loadedArtworkUri: String? = null

    private var chapters: List<Chapter> = emptyList()
    private var chaptersEpisodeId: Long? = null
    private var currentChapterIndex = -1
    private var chapterAdapter: ChapterAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setupTransitions()
        setContentView(binding.root)

        // Hold the enter transition until the artwork is ready so it grows cleanly from the
        // mini-player; a fallback starts it anyway if the controller is slow to connect.
        postponeEnterTransition()
        binding.root.postDelayed({ beginEnterTransition() }, 300)

        binding.playerClose.setOnClickListener { finishAfterTransition() }
        binding.playerPlayPause.setOnClickListener { playerConnection.togglePlayPause() }
        binding.playerSkipBack.setOnClickListener { playerConnection.seekBack() }
        binding.playerSkipForward.setOnClickListener { playerConnection.seekForward() }
        binding.playerSpeed.setOnClickListener { cycleSpeed() }
        binding.playerMarkPlayed.setOnClickListener { markPlayed() }
        binding.playerChapter.setOnClickListener { showChapterPicker() }

        binding.playerSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.playerPosition.text = Format.clock(progress * 1000L)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
                playerConnection.seekTo(seekBar.progress * 1000L)
            }
        })

        playerConnection.onUpdate = { render() }
    }

    /** Grow the artwork up out of the mini-player while the rest slides in from the bottom. */
    private fun setupTransitions() {
        // One UI's smooth standard easing curve.
        val easing = PathInterpolator(0.22f, 0.25f, 0f, 1f)
        val sharedElement = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeImageTransform())
            duration = 320
            interpolator = easing
        }
        window.sharedElementEnterTransition = sharedElement
        window.sharedElementReturnTransition = sharedElement

        val slide = Slide(Gravity.BOTTOM).apply {
            duration = 300
            interpolator = easing
            excludeTarget(R.id.player_art, true)
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }
        window.enterTransition = slide
        window.returnTransition = slide
    }

    private fun beginEnterTransition() {
        if (!enterTransitionStarted) {
            enterTransitionStarted = true
            startPostponedEnterTransition()
        }
    }

    private fun render() {
        val controller = playerConnection.controller ?: return
        if (controller.currentMediaItem == null) return

        val metadata = controller.mediaMetadata
        binding.playerTitle.text = metadata.title ?: ""
        binding.playerPodcast.text = metadata.artist ?: ""
        binding.playerPlayPause.setImageResource(
            if (controller.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
        binding.playerSpeed.text = formatSpeed(controller.playbackParameters.speed)

        // render() runs on every player tick (~2x/sec); only reload artwork when it actually
        // changes, otherwise begin the postponed enter transition straight away.
        val artworkUri = metadata.artworkUri?.toString()
        if (artworkUri != loadedArtworkUri) {
            loadedArtworkUri = artworkUri
            Glide.with(this)
                .load(metadata.artworkUri)
                .transform(RoundedCorners(32))
                .placeholder(R.drawable.bg_art_placeholder)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        binding.playerArt.post { beginEnterTransition() }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        binding.playerArt.post { beginEnterTransition() }
                        return false
                    }
                })
                .into(binding.playerArt)
        } else {
            beginEnterTransition()
        }

        val duration = controller.duration.takeIf { it > 0 } ?: 0L
        binding.playerSeekbar.max = (duration / 1000).toInt()
        binding.playerDuration.text = Format.clock(duration)
        if (!userSeeking) {
            binding.playerSeekbar.progress = (controller.currentPosition / 1000).toInt()
            binding.playerPosition.text = Format.clock(controller.currentPosition)
        }

        updateChapters(MediaItems.episodeId(controller.currentMediaItem), controller.currentPosition)
    }

    /** Loads chapters when the episode changes and tracks the active chapter as it plays. */
    private fun updateChapters(episodeId: Long?, positionMs: Long) {
        if (episodeId != chaptersEpisodeId) {
            chaptersEpisodeId = episodeId
            chapters = emptyList()
            currentChapterIndex = -1
            chapterAdapter = null
            binding.playerChapter.visibility = android.view.View.GONE
            if (episodeId != null) loadChapters(episodeId)
            return
        }
        if (chapters.isEmpty()) return

        val index = chapters.indexAt(positionMs)
        if (index != currentChapterIndex) {
            currentChapterIndex = index
            binding.playerChapter.text = chapterLabel(index)
            chapterAdapter?.setCurrentIndex(index)
        }
    }

    private fun loadChapters(episodeId: Long) {
        lifecycleScope.launch {
            val loaded = repository.ensureChapters(episodeId)
            // The user may have skipped to another episode while this was loading.
            if (chaptersEpisodeId != episodeId) return@launch
            chapters = loaded
            currentChapterIndex = -1
            if (loaded.isEmpty()) {
                binding.playerChapter.visibility = android.view.View.GONE
            } else {
                binding.playerChapter.visibility = android.view.View.VISIBLE
                val position = playerConnection.controller?.currentPosition ?: 0L
                currentChapterIndex = loaded.indexAt(position)
                binding.playerChapter.text = chapterLabel(currentChapterIndex)
            }
        }
    }

    private fun chapterLabel(index: Int): String =
        chapters.getOrNull(index)?.title ?: getString(R.string.chapters)

    private fun showChapterPicker() {
        if (chapters.isEmpty()) return
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chapters)
            .setView(recycler)
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener { chapterAdapter = null }
            .create()
        val adapter = ChapterAdapter(chapters, currentChapterIndex) { chapter ->
            playerConnection.seekTo(chapter.startMs)
            dialog.dismiss()
        }
        chapterAdapter = adapter
        recycler.adapter = adapter
        dialog.show()
        // Open scrolled to the chapter that's playing.
        currentChapterIndex.takeIf { it >= 0 }?.let { recycler.scrollToPosition(it) }
    }

    private fun cycleSpeed() {
        // Advance from the player's real current speed rather than a local index, so the chip
        // stays correct after reopening the player or a process restart.
        val current = playerConnection.controller?.playbackParameters?.speed ?: 1.0f
        val currentIndex = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        val speed = SPEEDS[(currentIndex + 1).mod(SPEEDS.size)]
        playerConnection.setSpeed(speed)
        binding.playerSpeed.text = formatSpeed(speed)
    }

    private fun formatSpeed(speed: Float): String = "${speed}×"

    private fun markPlayed() {
        val episodeId = MediaItems.episodeId(playerConnection.controller?.currentMediaItem) ?: return
        lifecycleScope.launch { repository.setPlayed(episodeId, true) }
        Toast.makeText(this, "Marked as played", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val SPEEDS = floatArrayOf(1.0f, 1.2f, 1.5f, 1.75f, 2.0f, 0.8f)
    }
}
