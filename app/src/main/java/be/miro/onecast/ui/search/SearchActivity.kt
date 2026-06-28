package be.miro.onecast.ui.search

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import be.miro.onecast.databinding.ActivitySearchBinding
import be.miro.onecast.podcastRepository
import kotlinx.coroutines.launch

/** Add a podcast by searching the iTunes directory or pasting an RSS feed URL. */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarLayout.setNavigationButtonAsBack()

        adapter = SearchResultAdapter(onClick = { subscribe(it.feedUrl) })
        binding.results.layoutManager = LinearLayoutManager(this)
        binding.results.adapter = adapter

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit()
                true
            } else {
                false
            }
        }
        binding.searchInput.requestFocus()
    }

    private fun submit() {
        val query = binding.searchInput.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        if (query.startsWith("http", ignoreCase = true)) subscribe(query) else search(query)
    }

    private fun search(term: String) {
        setLoading(true)
        binding.searchEmpty.visibility = View.GONE
        lifecycleScope.launch {
            val results = runCatching { podcastRepository.search(term) }.getOrDefault(emptyList())
            adapter.submit(results)
            setLoading(false)
            binding.searchEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun subscribe(feedUrl: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                podcastRepository.subscribe(feedUrl)
                Toast.makeText(this@SearchActivity, "Subscribed", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@SearchActivity,
                    "Couldn't add feed: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.searchProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }
}
