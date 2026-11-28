package be.miro.onecast.ui.subscriptions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.miro.onecast.R
import be.miro.onecast.data.Podcast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class PodcastGridAdapter(
    private val onClick: (Podcast) -> Unit,
) : ListAdapter<Podcast, PodcastGridAdapter.Holder>(Diff) {

    /**
     * Diffed rather than rebinding the whole grid: a feed refresh rewrites its podcast row (if only
     * to stamp the refresh time), and the home screen refreshes every stale feed on resume — so a
     * full rebind, with a Glide request per tile, ran once for every podcast refreshed.
     */
    fun submit(items: List<Podcast>) = submitList(items)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_podcast, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val art: ImageView = view.findViewById(R.id.grid_art)
        private val title: TextView = view.findViewById(R.id.grid_title)

        fun bind(podcast: Podcast) {
            title.text = podcast.title
            Glide.with(art)
                .load(podcast.artworkUrl)
                .transform(CenterCrop(), RoundedCorners(art.resources.getDimensionPixelSize(R.dimen.radius_sm)))
                .placeholder(R.drawable.bg_art_placeholder)
                .into(art)
            itemView.setOnClickListener { onClick(podcast) }
        }
    }

    /** Only what a tile shows counts as a change. */
    private object Diff : DiffUtil.ItemCallback<Podcast>() {
        override fun areItemsTheSame(old: Podcast, new: Podcast) = old.id == new.id
        override fun areContentsTheSame(old: Podcast, new: Podcast) =
            old.title == new.title && old.artworkUrl == new.artworkUrl
    }
}
