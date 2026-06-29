package be.miro.onecast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** User preferences backed by SharedPreferences, exposed as Flows so the UI re-renders on change. */
class AppSettings(private val prefs: SharedPreferences) {

    /** When true, episodes already marked played are omitted from episode lists. */
    var hidePlayedEpisodes: Boolean
        get() = prefs.getBoolean(KEY_HIDE_PLAYED, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_PLAYED, value).apply()

    fun observeHidePlayedEpisodes(): Flow<Boolean> = observeBoolean(KEY_HIDE_PLAYED) { hidePlayedEpisodes }

    /**
     * Id of the episode currently loaded into the player, or -1 if none. Persisted so a
     * picked-but-not-yet-played episode can be restored into the player after the (non-foreground,
     * killable) playback service is torn down on backgrounding.
     */
    var lastEpisodeId: Long
        get() = prefs.getLong(KEY_LAST_EPISODE, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_EPISODE, value).apply()

    private fun observeBoolean(key: String, read: () -> Boolean): Flow<Boolean> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_HIDE_PLAYED = "hide_played_episodes"
        private const val KEY_LAST_EPISODE = "last_episode_id"

        fun create(context: Context): AppSettings =
            AppSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
