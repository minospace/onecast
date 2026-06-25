package be.dimsumfamily.podcast.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Snapshot of "what the widget should currently show," persisted so the widget renders
 * correctly even when [be.dimsumfamily.podcast.playback.PlaybackService] isn't alive
 * (process killed, device rebooted).
 */
data class WidgetState(
    val episodeId: Long,
    val title: String,
    val podcastTitle: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
) {
    companion object {
        private const val PREFS_NAME = "widget_state"
        private const val KEY_EPISODE_ID = "episode_id"
        private const val KEY_TITLE = "title"
        private const val KEY_PODCAST_TITLE = "podcast_title"
        private const val KEY_ARTWORK_URL = "artwork_url"
        private const val KEY_IS_PLAYING = "is_playing"

        fun update(
            context: Context,
            episodeId: Long,
            title: String,
            podcastTitle: String,
            artworkUrl: String?,
            isPlaying: Boolean,
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_EPISODE_ID, episodeId)
                .putString(KEY_TITLE, title)
                .putString(KEY_PODCAST_TITLE, podcastTitle)
                .putString(KEY_ARTWORK_URL, artworkUrl)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()
        }

        /** Null if no episode has ever played. */
        fun read(context: Context): WidgetState? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val episodeId = prefs.getLong(KEY_EPISODE_ID, -1L)
            if (episodeId < 0) return null
            return WidgetState(
                episodeId = episodeId,
                title = prefs.getString(KEY_TITLE, "") ?: "",
                podcastTitle = prefs.getString(KEY_PODCAST_TITLE, "") ?: "",
                artworkUrl = prefs.getString(KEY_ARTWORK_URL, null),
                isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            )
        }

        fun notifyWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PodcastWidgetProvider::class.java))
            if (ids.isNotEmpty()) PodcastWidgetProvider.refreshWidgets(context, manager, ids)
        }
    }
}
