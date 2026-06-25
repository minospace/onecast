package be.dimsumfamily.podcast.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.dimsumfamily.podcast.R
import be.dimsumfamily.podcast.feed.PodcastSearchResult
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class SearchResultAdapter(
    private val onClick: (PodcastSearchResult) -> Unit,
) : RecyclerView.Adapter<SearchResultAdapter.Holder>() {

    private var items: List<PodcastSearchResult> = emptyList()

    fun submit(items: List<PodcastSearchResult>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val art: ImageView = view.findViewById(R.id.result_art)
        private val title: TextView = view.findViewById(R.id.result_title)
        private val author: TextView = view.findViewById(R.id.result_author)

        fun bind(result: PodcastSearchResult) {
            title.text = result.title
            author.text = result.author ?: ""
            Glide.with(art)
                .load(result.artworkUrl)
                .transform(RoundedCorners(16))
                .placeholder(R.drawable.bg_art_placeholder)
                .into(art)
            itemView.setOnClickListener { onClick(result) }
        }
    }
}
