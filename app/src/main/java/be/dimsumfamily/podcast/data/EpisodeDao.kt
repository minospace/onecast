package be.dimsumfamily.podcast.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun observeForPodcast(podcastId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeById(id: Long): Flow<Episode?>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): Episode?

    /** Only new items are written; existing (podcastId, guid) rows are kept as-is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>): List<Long>

    /** Marking played also clears the resume position so it won't auto-resume. */
    @Query(
        "UPDATE episodes SET isPlayed = :played, " +
            "positionMs = CASE WHEN :played THEN 0 ELSE positionMs END WHERE id = :id",
    )
    suspend fun setPlayed(id: Long, played: Boolean)

    /** Bulk mark every episode of a podcast played/unplayed; clears resume positions when played. */
    @Query(
        "UPDATE episodes SET isPlayed = :played, " +
            "positionMs = CASE WHEN :played THEN 0 ELSE positionMs END WHERE podcastId = :podcastId",
    )
    suspend fun setAllPlayed(podcastId: Long, played: Boolean)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long)

    @Query("UPDATE episodes SET durationMs = :durationMs WHERE id = :id AND durationMs <= 0")
    suspend fun updateDurationIfUnknown(id: Long, durationMs: Long)
}
