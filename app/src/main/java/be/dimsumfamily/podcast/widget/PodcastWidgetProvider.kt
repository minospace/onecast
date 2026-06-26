package be.dimsumfamily.podcast.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import be.dimsumfamily.podcast.R
import be.dimsumfamily.podcast.playback.PlaybackService
import be.dimsumfamily.podcast.ui.MainActivity
import be.dimsumfamily.podcast.ui.player.PlayerActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Compact home-screen widget: artwork, current/last episode, play/pause + skip-forward. */
class PodcastWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshWidgets(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> handleAction(context) { if (it.isPlaying) it.pause() else it.play() }
            ACTION_SKIP_FORWARD -> handleAction(context) { it.seekForward() }
            ACTION_SKIP_BACK -> handleAction(context) { it.seekBack() }
            else -> super.onReceive(context, intent)
        }
    }

    /**
     * Connects a one-shot [MediaController] to issue a single command. [goAsync] extends the
     * receiver's lifetime past `onReceive` returning, since the connection is asynchronous; a
     * timeout guards against a hung connection leaking the pending result.
     */
    private fun handleAction(context: Context, action: (MediaController) -> Unit) {
        val pendingResult = goAsync()
        // A BroadcastReceiver's own Context is bind-restricted; MediaController.buildAsync()
        // binds to PlaybackService internally, so it needs the real application Context.
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            future.cancel(false)
            pendingResult.finish()
        }
        timeoutHandler.postDelayed(timeoutRunnable, ACTION_TIMEOUT_MS)
        future.addListener(
            {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                runCatching { future.get() }.getOrNull()?.let { controller ->
                    action(controller)
                    controller.release()
                }
                pendingResult.finish()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    companion object {
        const val ACTION_TOGGLE = "be.dimsumfamily.podcast.widget.ACTION_TOGGLE"
        const val ACTION_SKIP_FORWARD = "be.dimsumfamily.podcast.widget.ACTION_SKIP_FORWARD"
        const val ACTION_SKIP_BACK = "be.dimsumfamily.podcast.widget.ACTION_SKIP_BACK"
        private const val ACTION_TIMEOUT_MS = 5_000L
        private const val ART_SIZE_PX = 256

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val state = WidgetState.read(context)
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, state, artwork = null))
                if (state?.artworkUrl != null) loadArtworkAndUpdate(context, manager, id, state)
            }
        }

        private fun loadArtworkAndUpdate(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            state: WidgetState,
        ) {
            scope.launch {
                val bitmap = runCatching {
                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(state.artworkUrl)
                        .transform(RoundedCorners(16))
                        .submit(ART_SIZE_PX, ART_SIZE_PX)
                        .get()
                }.getOrNull()
                if (bitmap != null) manager.updateAppWidget(id, buildViews(context, state, bitmap))
            }
        }

        private fun buildViews(context: Context, state: WidgetState?, artwork: Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_podcast)
            if (state == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_subtitle))
                views.setViewVisibility(R.id.widget_play_pause, View.GONE)
                views.setViewVisibility(R.id.widget_skip_back, View.GONE)
                views.setViewVisibility(R.id.widget_skip_forward, View.GONE)
                views.setViewVisibility(R.id.widget_progress, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_root, openActivityIntent(context, MainActivity::class.java))
                return views
            }
            views.setTextViewText(R.id.widget_title, state.title)
            views.setTextViewText(R.id.widget_subtitle, state.podcastTitle)
            views.setViewVisibility(R.id.widget_play_pause, View.VISIBLE)
            views.setViewVisibility(R.id.widget_skip_back, View.VISIBLE)
            views.setViewVisibility(R.id.widget_skip_forward, View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            )
            views.setViewVisibility(
                R.id.widget_progress,
                if (state.durationMs > 0) View.VISIBLE else View.GONE,
            )
            views.setProgressBar(R.id.widget_progress, 1000, state.progressPermille, false)
            if (artwork != null) views.setImageViewBitmap(R.id.widget_art, artwork)
            views.setOnClickPendingIntent(R.id.widget_root, openActivityIntent(context, PlayerActivity::class.java))
            views.setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_TOGGLE))
            views.setOnClickPendingIntent(R.id.widget_skip_back, actionIntent(context, ACTION_SKIP_BACK))
            views.setOnClickPendingIntent(R.id.widget_skip_forward, actionIntent(context, ACTION_SKIP_FORWARD))
            return views
        }

        private fun openActivityIntent(context: Context, activity: Class<*>): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, activity),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, PodcastWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
