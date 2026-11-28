package be.miro.onecast.ui.custom

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import be.miro.onecast.R
import be.miro.onecast.databinding.ActivityCustomPodcastBinding
import be.miro.onecast.podcastRepository
import be.miro.onecast.ui.AmoledTheme
import be.miro.onecast.ui.ExpressiveTheme
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.launch

/**
 * Create or edit a podcast of the user's own — name, author, description and cover. The audio that
 * goes in it is added afterwards, from the podcast screen.
 */
class CustomPodcastActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomPodcastBinding

    private var podcastId = 0L

    /** A cover the user just picked, copied into app storage only once they save. */
    private var pickedArtwork: Uri? = null

    private var amoledApplied = false
    private var expressiveApplied = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        pickedArtwork = uri
        showCover(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must precede inflation — the accent overlay only reaches views inflated after it.
        ExpressiveTheme.apply(this)
        expressiveApplied = ExpressiveTheme.isActive(this)
        binding = ActivityCustomPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        amoledApplied = AmoledTheme.isActive(this)

        podcastId = intent.getLongExtra(EXTRA_PODCAST_ID, 0L)
        binding.toolbarLayout.setNavigationButtonAsBack()
        binding.toolbarLayout.setTitle(
            getString(if (podcastId == 0L) R.string.custom_create_title else R.string.custom_edit_title),
        )

        binding.coverArt.setOnClickListener { pickImage.launch("image/*") }
        binding.coverAction.setOnClickListener { pickImage.launch("image/*") }
        binding.saveButton.setOnClickListener { save() }

        // After a rotation/recreate the text fields restore themselves; only the picked cover (which
        // isn't a view's state) needs carrying over — and the saved podcast must not overwrite either.
        pickedArtwork = savedInstanceState?.let {
            @Suppress("DEPRECATION")
            it.getParcelable<Uri>(STATE_PICKED_ARTWORK)
        }
        pickedArtwork?.let { showCover(it) }
        if (podcastId != 0L) {
            loadExisting(fillFields = savedInstanceState == null)
        } else if (savedInstanceState == null) {
            binding.nameInput.requestFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickedArtwork?.let { outState.putParcelable(STATE_PICKED_ARTWORK, it) }
    }

    override fun onResume() {
        super.onResume()
        if (AmoledTheme.isActive(this) != amoledApplied ||
            ExpressiveTheme.isActive(this) != expressiveApplied
        ) {
            recreate()
        }
    }

    private fun loadExisting(fillFields: Boolean) {
        lifecycleScope.launch {
            val podcast = podcastRepository.getPodcast(podcastId) ?: return@launch
            if (fillFields) {
                binding.nameInput.setText(podcast.title)
                binding.authorInput.setText(podcast.author.orEmpty())
                binding.descriptionInput.setText(podcast.description.orEmpty())
            }
            // A freshly picked cover (restored above) wins over the saved one.
            if (pickedArtwork == null) podcast.artworkUrl?.let {
                showCover(Uri.parse(it))
                binding.coverAction.setText(R.string.custom_cover_change)
            }
        }
    }

    /** Swap the headphones placeholder for the real picture, filling the square. */
    private fun showCover(uri: Uri) {
        binding.coverArt.setPadding(0, 0, 0, 0)
        binding.coverArt.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.coverAction.setText(R.string.custom_cover_change)
        Glide.with(binding.coverArt)
            .load(uri)
            .transform(RoundedCorners(24))
            .into(binding.coverArt)
    }

    private fun save() {
        val name = binding.nameInput.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameInput.error = getString(R.string.custom_name_required)
            binding.nameInput.requestFocus()
            return
        }
        binding.saveButton.isEnabled = false
        lifecycleScope.launch {
            val id = runCatching {
                podcastRepository.saveLocalPodcast(
                    podcastId = podcastId,
                    title = name,
                    author = binding.authorInput.text.toString().trim().takeIf { it.isNotEmpty() },
                    description = binding.descriptionInput.text.toString().trim()
                        .takeIf { it.isNotEmpty() },
                    artworkUri = pickedArtwork,
                )
            }.getOrNull()
            if (id == null || id <= 0L) {
                binding.saveButton.isEnabled = true
                Toast.makeText(this@CustomPodcastActivity, R.string.custom_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PODCAST_ID, id))
            finish()
        }
    }

    companion object {
        /** In: the podcast to edit (absent or 0 to create one). Out: the id that was saved. */
        const val EXTRA_PODCAST_ID = "podcast_id"
        private const val STATE_PICKED_ARTWORK = "picked_artwork"

        fun createIntent(context: Context): Intent =
            Intent(context, CustomPodcastActivity::class.java)

        fun editIntent(context: Context, podcastId: Long): Intent =
            createIntent(context).putExtra(EXTRA_PODCAST_ID, podcastId)
    }
}
