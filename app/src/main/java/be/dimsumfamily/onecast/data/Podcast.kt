package be.dimsumfamily.onecast.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A subscribed podcast (one RSS feed). */
@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["feedUrl"], unique = true)],
)
data class Podcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val lastRefreshed: Long = 0,
)
