package com.test1.tv.ui.traktlist

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.AccentColorCache
import com.test1.tv.ui.HeroBackgroundController
import com.test1.tv.ui.HeroLogoLoader
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.HeroSyncManager
import com.test1.tv.ui.RowLayoutHelper
import com.test1.tv.ui.RowPrefetchManager
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.ContentRowAdapter
import com.test1.tv.ui.adapter.RowPresentation
import com.test1.tv.databinding.FragmentTraktListBinding
import com.test1.tv.ui.WatchedBadgeManager
import com.test1.tv.ui.contextmenu.ContextMenuActionHandler
import com.test1.tv.ui.contextmenu.ContextMenuHelper
import com.test1.tv.ui.contextmenu.shouldShowContextMenu
import com.test1.tv.data.repository.TraktStatusProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TraktListFragment : Fragment() {

    companion object {
        fun newInstance(
            username: String,
            listId: String,
            title: String,
            traktUrl: String
        ): TraktListFragment {
            return TraktListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                    putString(ARG_LIST_ID, listId)
                    putString(ARG_TITLE, title)
                    putString(ARG_TRAKT_URL, traktUrl)
                }
            }
        }
    }

    private var _binding: FragmentTraktListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TraktListViewModel by viewModels()

    @Inject lateinit var sharedViewPool: RecyclerView.RecycledViewPool
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var rowPrefetchManager: RowPrefetchManager
    @Inject lateinit var contextMenuActionHandler: ContextMenuActionHandler
    @Inject lateinit var traktStatusProvider: TraktStatusProvider
    @Inject lateinit var watchedBadgeManager: WatchedBadgeManager

    private lateinit var contextMenuHelper: ContextMenuHelper
    private lateinit var heroBackgroundController: HeroBackgroundController
    private lateinit var heroLogoLoader: HeroLogoLoader
    private lateinit var heroSyncManager: HeroSyncManager

    private lateinit var rowAdapter: ContentRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTraktListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        heroBackgroundController = HeroBackgroundController(
            fragment = this,
            backdropView = binding.heroBackdrop,
            ambientOverlay = binding.ambientBackgroundOverlay,
            defaultAmbientColor = Color.parseColor("#0A0F1F")
        )
        heroLogoLoader = HeroLogoLoader(
            fragment = this,
            logoView = binding.heroLogo,
            titleView = binding.heroTitle,
            maxWidthRes = R.dimen.hero_logo_max_width,
            maxHeightRes = R.dimen.hero_logo_max_height
        )
        heroSyncManager = HeroSyncManager(viewLifecycleOwner) { content ->
            updateHeroSection(content)
        }

        contextMenuHelper = ContextMenuHelper(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            actionHandler = contextMenuActionHandler,
            traktStatusProvider = traktStatusProvider,
            onWatchedStateChanged = { tmdbId, type, isWatched ->
                viewLifecycleOwner.lifecycleScope.launch {
                    watchedBadgeManager.notifyWatchedStateChanged(tmdbId, type, isWatched)
                }
            }
        )

        rowAdapter = ContentRowAdapter(
            initialRows = emptyList(),
            onItemClick = ::handleItemClick,
            onItemFocused = { item, rowIndex, _ ->
                heroSyncManager.onContentSelected(item)
                rowPrefetchManager.onRowFocused(rowIndex, rowAdapter.currentRows())
            },
            onNavigateToNavBar = {},
            onItemLongPress = { item -> handleItemLongPress(item) },
            onRequestMore = {},
            viewPool = sharedViewPool,
            accentColorCache = accentColorCache,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )

        RowLayoutHelper.configureVerticalGrid(binding.contentRows)
        binding.contentRows.apply {
            adapter = rowAdapter
            setHasFixedSize(true)
            clipToPadding = false
            clipChildren = false
        }

        viewModel.rowItems.observe(viewLifecycleOwner) { items ->
            val row = ContentRow(
                title = viewModel.title,
                items = items.toMutableList(),
                presentation = RowPresentation.PORTRAIT
            )
            rowAdapter.updateRows(listOf(row))
            items.firstOrNull()?.let { heroSyncManager.updateHeroImmediate(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.heroContent.observe(viewLifecycleOwner) { item ->
            item?.let { updateHeroSection(it) }
        }

        // Subscribe to badge updates for immediate UI refresh
        viewLifecycleOwner.lifecycleScope.launch {
            watchedBadgeManager.badgeUpdates.collectLatest { update ->
                rowAdapter.updateBadgeForItem(update.tmdbId)
            }
        }
    }

    private fun handleItemClick(item: ContentItem, posterView: ImageView) {
        startActivity(Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.CONTENT_ITEM, item)
        })
    }

    private fun handleItemLongPress(item: ContentItem) {
        if (item.shouldShowContextMenu()) {
            contextMenuHelper.showContextMenu(item)
        }
    }

    private fun updateHeroSection(item: ContentItem) {
        binding.heroTitle.text = item.title
        HeroSectionHelper.updateHeroMetadata(binding.heroMetadata, item)
        val overviewText = HeroSectionHelper.buildOverviewText(item)
        binding.heroOverview.text = overviewText
        binding.heroOverview.visibility = if (overviewText.isNullOrBlank()) View.GONE else View.VISIBLE
        heroLogoLoader.load(item.logoUrl)
        val defaultFallback: Drawable? = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        heroBackgroundController.updateBackdrop(item.backdropUrl, defaultFallback)
    }

    override fun onDestroyView() {
        heroLogoLoader.cancel()
        _binding = null
        super.onDestroyView()
    }
}
