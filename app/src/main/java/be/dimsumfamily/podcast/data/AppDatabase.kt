package be.dimsumfamily.podcast.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Podcast::class, Episode::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Adds nullable chapter columns; existing rows default to "no chapters". */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN chapters TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE episodes ADD COLUMN chaptersUrl TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "podcast.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
