package be.dimsumfamily.onecast.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<Podcast>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observeById(id: Long): Flow<Podcast?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getById(id: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): Podcast?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: Podcast): Long

    @Update
    suspend fun update(podcast: Podcast)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
