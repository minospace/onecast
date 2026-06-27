package be.dimsumfamily.onecast.data

import androidx.room.TypeConverter

/** Room type converters. Stores a [Chapter] list as a JSON string column. */
class Converters {

    @TypeConverter
    fun fromChapters(chapters: List<Chapter>?): String = ChapterJson.encode(chapters ?: emptyList())

    @TypeConverter
    fun toChapters(json: String?): List<Chapter> = ChapterJson.decode(json)
}
