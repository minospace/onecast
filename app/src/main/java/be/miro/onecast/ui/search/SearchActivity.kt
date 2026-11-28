package be.miro.onecast.ui.search

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import be.miro.onecast.R
import be.miro.onecast.databinding.ActivitySearchBinding
import be.miro.onecast.feed.PodcastSearchResult
import be.miro.onecast.podcastRepository
import be.miro.onecast.ui.AmoledTheme
import be.miro.onecast.ui.ExpressiveTheme
import be.miro.onecast.ui.GroupedListDecoration
import be.miro.onecast.ui.custom.CustomPodcastActivity
import be.miro.onecast.ui.podcast.PodcastActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Add a podcast by searching the iTunes directory or pasting an RSS feed URL. */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private var amoledApplied = false
    private var expressiveApplied = false
    private var searchJob: Job? = null

    /**
     * Land on the new podcast once it's created, with Home behind it — this screen has done its
     * job, and the next step is adding audio to it.
     */
    private val createCustom = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = result.data?.getLongExtra(CustomPodcastActivity.EXTRA_PODCAST_ID, 0L) ?: 0L
        if (result.resultCode == RESULT_OK && id != 0L) {
            PodcastActivity.start(this, id)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must precede inflation — the accent overlay only reaches views inflated after it.
        ExpressiveTheme.apply(this)
        expressiveApplied = ExpressiveTheme.isActive(this)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        amoledApplied = AmoledTheme.isActive(this)

        binding.toolbarLayout.setNavigationButtonAsBack()

        adapter = SearchResultAdapter(onClick = { subscribe(it.feedUrl) })
        binding.results.layoutManager = LinearLayoutManager(this)
        binding.results.adapter = adapter
        // The results are one group; hairlines start past the artwork (row padding + 52dp + 16dp).
        val dividerInset = resources.getDimensionPixelSize(R.dimen.row_padding_horizontal) +
            ((52 + 16) * resources.displayMetrics.density).toInt()
        binding.results.addItemDecoration(GroupedListDecoration(this, dividerInset) { RESULTS_GROUP })
        GroupedListDecoration.applySingleRow(binding.createCustom)

        // Results follow the text as it's typed (after a short pause, so each keystroke doesn't
        // fire a request). A pasted feed link isn't searched — it's added with the keyboard's
        // Search key instead, which the hint under the field then says.
        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            binding.searchClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            val isUrl = query.isFeedUrl()
            binding.searchHelp.setText(if (isUrl) R.string.search_help_url else R.string.search_help)
            searchJob?.cancel()
            when {
                isUrl -> Unit
                query.length < MIN_QUERY_LENGTH -> showResults(emptyList(), searched = false)
                else -> searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    search(query)
                }
            }
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit()
                true
            } else {
                false
            }
        }
        binding.searchClear.setOnClickListener { binding.searchInput.text = null }
        binding.createCustom.setOnClickListener {
            createCustom.launch(CustomPodcastActivity.createIntent(this))
        }

        binding.searchInput.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (AmoledTheme.isActive(this) != amoledApplied ||
            ExpressiveTheme.isActive(this) != expressiveApplied
        ) {
            recreate()
        }
    }

    /** The keyboard's Search key: add a pasted feed link, or search right away without waiting. */
    private fun submit() {
        val query = binding.searchInput.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        searchJob?.cancel()
        if (query.isFeedUrl()) {
            subscribe(query)
        } else {
            searchJob = lifecycleScope.launch { search(query) }
        }
    }

    private suspend fun search(term: String) {
        setLoading(true)
        binding.searchEmpty.visibility = View.GONE
        val outcome = runCatching { podcastRepository.search(term) }
        // A newer keystroke cancelled this search while it was in flight: leave the screen to it.
        currentCoroutineContext().ensureActive()
        setLoading(false)
        outcome.onSuccess { showResults(it, searched = true) }
            .onFailure {
                if (it is CancellationException) throw it
                showResults(emptyList(), searched = false)
                binding.searchEmpty.setText(R.string.search_failed)
                binding.searchEmpty.visibility = View.VISIBLE
            }
    }

    private fun showResults(results: List<PodcastSearchResult>, searched: Boolean) {
        adapter.submit(results)
        binding.searchEmpty.setText(R.string.search_empty)
        binding.searchEmpty.visibility = if (searched && results.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun String.isFeedUrl(): Boolean = startsWith("http", ignoreCase = true)

    private fun subscribe(feedUrl: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                podcastRepository.subscribe(feedUrl)
                Toast.makeText(this@SearchActivity, R.string.subscribed, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@SearchActivity,
                    getString(R.string.subscribe_failed, e.message.orEmpty()),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.searchProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private companion object {
        const val RESULTS_GROUP = "results"
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }
}
