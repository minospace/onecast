package be.dimsumfamily.onecast.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import be.dimsumfamily.onecast.R
import be.dimsumfamily.onecast.databinding.ActivityMainBinding
import be.dimsumfamily.onecast.ui.player.PlayerActivity
import be.dimsumfamily.onecast.ui.podcast.PodcastActivity
import be.dimsumfamily.onecast.ui.search.SearchActivity
import be.dimsumfamily.onecast.ui.subscriptions.PodcastGridAdapter
import kotlinx.coroutines.launch

/** Home: the grid of subscribed podcasts. */
class MainActivity : MediaActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PodcastGridAdapter

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val spanCount = (resources.configuration.screenWidthDp / 120).coerceAtLeast(2)
        adapter = PodcastGridAdapter { PodcastActivity.start(this, it.id) }
        binding.podcastGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.podcastGrid.adapter = adapter

        binding.miniPlayer.bind(playerConnection) { openPlayer() }
        playerConnection.onUpdate = { binding.miniPlayer.refresh(playerConnection) }

        requestNotificationPermissionIfNeeded()
        observe()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { repository.refreshStalePodcasts() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_add) {
            startActivity(Intent(this, SearchActivity::class.java))
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    /** Open the full player, growing the artwork out of the mini-player as a shared element. */
    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, binding.miniPlayer.artView, "player_art",
        )
        startActivity(intent, options.toBundle())
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observePodcasts().collect { podcasts ->
                    adapter.submit(podcasts)
                    binding.emptyView.visibility = if (podcasts.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
