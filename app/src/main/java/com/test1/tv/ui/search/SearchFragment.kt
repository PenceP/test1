package com.test1.tv.ui.search

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.test1.tv.ActorDetailsActivity
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.SearchRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.ContentRowAdapter
import com.test1.tv.ui.adapter.RowPresentation
import com.test1.tv.ui.AccentColorCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private lateinit var searchField: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var rowsRecycler: RecyclerView
    private lateinit var rowsAdapter: ContentRowAdapter
    private lateinit var backgroundImageView: ImageView
    private val rowsState = mutableListOf<ContentRow>()
    private var currentBackdropUrl: String? = null

    private lateinit var viewModel: SearchViewModel
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var tmdbApiService: com.test1.tv.data.remote.api.TMDBApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupViewModel()
        setupSearchInput()
        observeResults()
    }

    private fun initViews(view: View) {
        searchField = view.findViewById(R.id.search_field)
        voiceButton = view.findViewById(R.id.voice_button)
        loadingIndicator = view.findViewById(R.id.search_loading)
        rowsRecycler = view.findViewById(R.id.search_rows)
        backgroundImageView = view.findViewById(R.id.search_background)

        rowsState.clear()
        rowsState.add(ContentRow(getString(R.string.search_movies), mutableListOf(), RowPresentation.PORTRAIT))
        rowsState.add(ContentRow(getString(R.string.search_shows), mutableListOf(), RowPresentation.PORTRAIT))
        rowsState.add(ContentRow(getString(R.string.search_people), mutableListOf(), RowPresentation.PORTRAIT))

        rowsAdapter = ContentRowAdapter(
            initialRows = rowsState,
            onItemClick = { item, _ -> handleItemClick(item) },
            onItemFocused = { item, _, _ -> updateDynamicBackground(item) },
            onNavigateToNavBar = { },
            onItemLongPress = { },
            onRequestMore = { },
            viewPool = null,
            accentColorCache = accentColorCache,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )
        rowsRecycler.layoutManager = LinearLayoutManager(requireContext())
        rowsRecycler.adapter = rowsAdapter

        voiceButton.isFocusable = true
        voiceButton.isFocusableInTouchMode = true
        voiceButton.setOnClickListener { handleVoiceClick() }
    }

    private fun setupViewModel() {
        val repo = SearchRepository(tmdbApiService)
        val factory = SearchViewModelFactory(repo)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    private fun setupSearchInput() {
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        searchField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = searchField.text.toString()
        viewModel.search(query)
    }

    private fun handleVoiceClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        searchField.requestFocus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            searchField.requestFocus()
        }
    }

    private fun observeResults() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.movieResults.observe(viewLifecycleOwner) { items ->
            updateRow(0, items)
        }

        viewModel.showResults.observe(viewLifecycleOwner) { items ->
            updateRow(1, items)
        }

        viewModel.peopleResults.observe(viewLifecycleOwner) { items ->
            updateRow(2, items)
        }
    }

    private fun updateRow(index: Int, items: List<ContentItem>) {
        val row = rowsState.getOrNull(index) ?: return

        // Create a new list instead of modifying the existing one
        val newItems = if (items.isNotEmpty()) {
            if (index == 2) {
                // mark people to route to actor page
                items.map { it.copy(cast = "__PERSON__") }
            } else {
                items
            }
        } else {
            listOf(
                ContentItem(
                    id = -1,
                    tmdbId = -1,
                    title = getString(R.string.search_empty),
                    overview = null,
                    posterUrl = null,
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.MOVIE,
                    runtime = null,
                    cast = if (index == 2) "__PERSON_PLACEHOLDER__" else null,
                    certification = null,
                    imdbId = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null
                )
            )
        }

        // Create a new ContentRow object with the new items
        rowsState[index] = ContentRow(
            title = row.title,
            items = newItems.toMutableList(),
            presentation = row.presentation
        )

        rowsAdapter.updateRows(rowsState.toList())
    }

    private fun handleItemClick(item: ContentItem) {
        if (item.tmdbId == -1) return
        if (item.cast == "__PERSON_PLACEHOLDER__") {
            return
        }
        if (item.cast == "__PERSON__") {
            ActorDetailsActivity.start(
                context = requireContext(),
                personId = item.tmdbId,
                personName = item.title,
                originContent = null
            )
        } else {
            DetailsActivity.start(requireContext(), item)
        }
    }

    /**
     * Updates the dynamic background based on the focused content item
     */
    private fun updateDynamicBackground(item: ContentItem) {
        // Skip if it's a placeholder or person result
        if (item.tmdbId == -1 || item.cast == "__PERSON__" || item.cast == "__PERSON_PLACEHOLDER__") {
            return
        }

        // Only update if the backdrop URL is different
        val backdropUrl = item.backdropUrl
        if (backdropUrl == currentBackdropUrl) {
            return
        }

        currentBackdropUrl = backdropUrl

        if (backdropUrl.isNullOrBlank()) {
            // Fade to dark background if no backdrop
            backgroundImageView.animate()
                .alpha(0f)
                .setDuration(300)
                .start()
        } else {
            // Load the backdrop with a crossfade
            Glide.with(this)
                .load(backdropUrl)
                .transition(DrawableTransitionOptions.withCrossFade(400))
                .into(backgroundImageView)

            // Ensure the background is visible
            if (backgroundImageView.alpha < 0.3f) {
                backgroundImageView.animate()
                    .alpha(0.3f)
                    .setDuration(400)
                    .start()
            }
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 701
    }
}
