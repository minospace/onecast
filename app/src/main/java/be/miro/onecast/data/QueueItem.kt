package be.miro.onecast.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One entry in the "Up Next" queue: an [Episode] scheduled to autoplay after the current one.
 * [episodeId] is the primary key, so the same episode can't appear twice; [position] is a sparse
 * ordering key (lower plays first). Deleting the episode (or unsubscribing its podcast) cascades
 * the queue row away.
 */
@Entity(
    tableName = "queue",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueueItem(
    @PrimaryKey val episodeId: Long,
    val position: Long,
)
