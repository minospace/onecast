package be.dimsumfamily.podcast.ui.subscriptions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.dimsumfamily.podcast.R
import be.dimsumfamily.podcast.data.Podcast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class PodcastGridAdapter(
    private val onClick: (Podcast) -> Unit,
) : RecyclerView.Adapter<PodcastGridAdapter.Holder>() {

    private var items: List<Podcast> = emptyList()

    fun submit(items: List<Podcast>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_podcast, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val art: ImageView = view.findViewById(R.id.grid_art)
        private val title: TextView = view.findViewById(R.id.grid_title)

        fun bind(podcast: Podcast) {
            title.text = podcast.title
            Glide.with(art)
                .load(podcast.artworkUrl)
                .transform(RoundedCorners(20))
                .placeholder(R.drawable.bg_art_placeholder)
                .into(art)
            itemView.setOnClickListener { onClick(podcast) }
        }
    }
}
