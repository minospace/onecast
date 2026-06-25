package be.dimsumfamily.podcast.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import be.dimsumfamily.podcast.R
import be.dimsumfamily.podcast.playback.PlayerConnection
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

/** Persistent bottom mini-player; shows the currently loaded episode and a play/pause toggle. */
class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val card: View
    private val art: ImageView
    private val title: TextView
    private val subtitle: TextView
    private val playPause: ImageButton
    private val skipForward: ImageButton
    private val progress: ProgressBar

    /** The artwork view, exposed so the host can use it as a shared element into the full player. */
    val artView: ImageView get() = art

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_mini_player, this, true)
        card = findViewById(R.id.mini_card)
        art = findViewById(R.id.mini_art)
        title = findViewById(R.id.mini_title)
        subtitle = findViewById(R.id.mini_subtitle)
        playPause = findViewById(R.id.mini_play_pause)
        skipForward = findViewById(R.id.mini_skip_forward)
        progress = findViewById(R.id.mini_progress)
        visibility = GONE
    }

    /** Wire click handlers once. The hosting activity drives [refresh] from its update callback. */
    fun bind(connection: PlayerConnection, onOpenPlayer: () -> Unit) {
        playPause.setOnClickListener { connection.togglePlayPause() }
        skipForward.setOnClickListener { connection.seekForward() }
        card.setOnClickListener { onOpenPlayer() }
    }

    fun refresh(connection: PlayerConnection) {
        val c = connection.controller
        val item = c?.currentMediaItem
        if (c == null || item == null) {
            visibility = GONE
            return
        }
        visibility = View.VISIBLE
        val md = c.mediaMetadata
        title.text = md.title ?: ""
        subtitle.text = md.artist ?: ""
        playPause.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        val duration = c.duration
        progress.progress = if (duration > 0) ((c.currentPosition * 1000) / duration).toInt() else 0
        Glide.with(art)
            .load(md.artworkUri)
            .transform(RoundedCorners(16))
            .placeholder(R.drawable.bg_art_placeholder)
            .into(art)
    }
}
