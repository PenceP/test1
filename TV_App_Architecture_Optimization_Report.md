# Android TV App Architecture Optimization & Configuration-Driven UI Report

**Prepared for:** test1.tv Development Team  
**Date:** December 2024

---

## Executive Summary

This report provides a comprehensive analysis of your Android TV application's architecture, focusing on three critical areas: database consolidation, configuration-driven UI architecture, and row/page management optimization. The current implementation shows strong foundations but has significant opportunities for improvement in maintainability, scalability, and user customization.

### Key Findings Overview

| Area | Current State & Recommendation |
|------|-------------------------------|
| **Database Architecture** | Single AppDatabase with 6 DAOs. Well-structured but row configuration stored in code, not database. |
| **Row Configuration** | Hardcoded in ViewModels (HomeViewModel, MoviesViewModel, TvShowsViewModel). Needs migration to unified config system. |
| **User Customization** | Not currently supported. Architecture changes proposed to enable row ordering/visibility in Settings. |
| **Code Duplication** | Significant duplication across MoviesViewModel, TvShowsViewModel, and HomeViewModel. Consolidation needed. |

---

## 1. Current Architecture Analysis

### 1.1 Database Structure

Your application uses a single Room database (AppDatabase) at version 11 with the following entities and DAOs:

- **CachedContent / CachedContentDao** - General content caching with category-based queries
- **TraktAccount / TraktAccountDao** - User authentication and profile data
- **TraktUserItem / TraktUserItemDao** - User's collection and watchlist items
- **ContinueWatchingEntity / ContinueWatchingDao** - Resume playback tracking
- **WatchStatusEntity / WatchStatusDao** - Watch progress and status
- **MediaContentEntity + MediaImageEntity + MediaRatingEntity / MediaDao** - Normalized media storage with split tables for efficient updates

> **Finding:** You are NOT using multiple database files. All data resides in a single database, which is correct. The concern about "multiple databases" is unfounded - your architecture is already consolidated.

### 1.2 Row Building Process Analysis

The current row building happens in three separate ViewModels with duplicated logic:

#### HomeViewModel (Lines 10720-11429)
- Uses HomeConfigRepository to load JSON configuration
- Supports HomeRowType enum: TRAKT_LIST, COLLECTION, CONTINUE_WATCHING
- Falls back to hardcoded defaults if config is empty
- Has `buildRows()` and `buildRowsFromConfig()` methods

#### MoviesViewModel (Lines 11662-11851)
- Completely hardcoded row definitions
- Creates: "Trending Movies", "Popular Movies", "Latest 4K Releases"
- No configuration support whatsoever
- Manual pagination tracking (trendingPage, popularPage)

#### TvShowsViewModel (Similar pattern)
- Duplicates the same hardcoded pattern as MoviesViewModel
- Creates show-specific rows with identical structure

---

## 2. Critical Problems Identified

### 2.1 Inconsistent Configuration

HomeViewModel uses HomeConfigRepository with JSON files, but MoviesViewModel and TvShowsViewModel completely ignore this pattern. This creates a fragmented system where:

- Home screen is partially configurable
- Movies and TV Shows screens are completely hardcoded
- No unified approach to row management

### 2.2 Code Duplication

The ContentRowState data class and row loading logic is duplicated across all three ViewModels. Each has its own:

- Pagination state tracking
- Row building methods
- LiveData publishers for content rows
- Hero content management

### 2.3 No User Customization Support

The current architecture has no mechanism for users to:

- Reorder rows
- Hide/show specific rows
- Customize which content appears on which screen
- Persist preferences across app sessions

---

## 3. Proposed Unified Architecture

### 3.1 New Database Entities

Add new Room entities to store row configuration and user preferences:

#### RowConfigEntity

```kotlin
@Entity(tableName = "row_config",
    indices = [Index(value = ["screenType", "position"])])
data class RowConfigEntity(
    @PrimaryKey val id: String,           // Unique row identifier
    val screenType: String,               // "home", "movies", "tvshows"
    val title: String,                    // Display title
    val rowType: String,                  // "trending", "popular", "continue", etc.
    val contentType: String?,             // "movies", "shows", null for mixed
    val presentation: String,             // "portrait", "landscape"
    val dataSourceUrl: String?,           // For Trakt list URLs
    val defaultPosition: Int,             // Default sort order
    val position: Int,                    // User-customized position
    val enabled: Boolean = true,          // User can hide rows
    val requiresAuth: Boolean = false,    // Needs Trakt login
    val pageSize: Int = 20,
    val isSystemRow: Boolean = false      // Can't be deleted, only hidden
)
```

#### RowConfigDao

```kotlin
@Dao
interface RowConfigDao {
    @Query("SELECT * FROM row_config WHERE screenType = :screen
            AND enabled = 1 ORDER BY position ASC")
    fun getEnabledRowsForScreen(screen: String): Flow<List<RowConfigEntity>>

    @Query("SELECT * FROM row_config WHERE screenType = :screen ORDER BY position")
    fun getAllRowsForScreen(screen: String): Flow<List<RowConfigEntity>>

    @Update
    suspend fun updateRow(row: RowConfigEntity)

    @Query("UPDATE row_config SET enabled = :enabled WHERE id = :rowId")
    suspend fun setRowEnabled(rowId: String, enabled: Boolean)

    @Query("UPDATE row_config SET position = :newPos WHERE id = :rowId")
    suspend fun updateRowPosition(rowId: String, newPos: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RowConfigEntity>)

    @Query("UPDATE row_config SET position = defaultPosition, enabled = 1")
    suspend fun resetToDefaults()
}
```

### 3.2 Unified Configuration Repository

Replace HomeConfigRepository with a unified ScreenConfigRepository that handles all screens:

```kotlin
@Singleton
class ScreenConfigRepository @Inject constructor(
    private val rowConfigDao: RowConfigDao,
    @ApplicationContext private val context: Context
) {
    enum class ScreenType(val key: String) {
        HOME("home"), MOVIES("movies"), TV_SHOWS("tvshows")
    }

    fun getRowsForScreen(screen: ScreenType): Flow<List<RowConfigEntity>> =
        rowConfigDao.getEnabledRowsForScreen(screen.key)

    fun getAllRowsForSettings(screen: ScreenType): Flow<List<RowConfigEntity>> =
        rowConfigDao.getAllRowsForScreen(screen.key)

    suspend fun toggleRowVisibility(rowId: String, enabled: Boolean) =
        rowConfigDao.setRowEnabled(rowId, enabled)

    suspend fun reorderRow(rowId: String, newPosition: Int) =
        rowConfigDao.updateRowPosition(rowId, newPosition)

    suspend fun initializeDefaults() {
        val existing = rowConfigDao.getEnabledRowsForScreen("home").first()
        if (existing.isEmpty()) {
            rowConfigDao.insertAll(DefaultRowConfigs.all)
        }
    }
}
```

### 3.3 Default Row Configurations

Define all default rows in a single object that maps your HOME.json structure to database entities:

```kotlin
object DefaultRowConfigs {
    val homeRows = listOf(
        RowConfigEntity(
            id = "home_continue_watching",
            screenType = "home",
            title = "Continue Watching",
            rowType = "continue_watching",
            presentation = "landscape",
            defaultPosition = 0, position = 0,
            requiresAuth = true, isSystemRow = true
        ),
        RowConfigEntity(
            id = "home_networks",
            screenType = "home",
            title = "Networks",
            rowType = "networks",
            presentation = "landscape",
            defaultPosition = 1, position = 1
        ),
        RowConfigEntity(
            id = "home_franchises",
            screenType = "home",
            title = "Franchises",
            rowType = "collections",
            presentation = "portrait",
            defaultPosition = 2, position = 2
        ),
        RowConfigEntity(
            id = "home_directors",
            screenType = "home",
            title = "Directors",
            rowType = "directors",
            presentation = "landscape",
            defaultPosition = 3, position = 3
        ),
        RowConfigEntity(
            id = "home_trending_movies",
            screenType = "home",
            title = "Trending Movies",
            rowType = "trending",
            contentType = "movies",
            presentation = "portrait",
            defaultPosition = 4, position = 4
        ),
        RowConfigEntity(
            id = "home_trending_shows",
            screenType = "home",
            title = "Trending Shows",
            rowType = "trending",
            contentType = "shows",
            presentation = "landscape",
            defaultPosition = 5, position = 5
        )
    )

    val moviesRows = listOf(
        RowConfigEntity(
            id = "movies_trending",
            screenType = "movies",
            title = "Trending Movies",
            rowType = "trending",
            contentType = "movies",
            presentation = "portrait",
            defaultPosition = 0, position = 0, isSystemRow = true
        ),
        RowConfigEntity(
            id = "movies_popular",
            screenType = "movies",
            title = "Popular Movies",
            rowType = "popular",
            contentType = "movies",
            presentation = "portrait",
            defaultPosition = 1, position = 1
        ),
        RowConfigEntity(
            id = "movies_4k",
            screenType = "movies",
            title = "Latest 4K Releases",
            rowType = "4k_releases",
            contentType = "movies",
            presentation = "portrait",
            defaultPosition = 2, position = 2
        )
    )

    val tvShowsRows = listOf(
        RowConfigEntity(
            id = "tvshows_trending",
            screenType = "tvshows",
            title = "Trending Shows",
            rowType = "trending",
            contentType = "shows",
            presentation = "landscape",
            defaultPosition = 0, position = 0, isSystemRow = true
        ),
        RowConfigEntity(
            id = "tvshows_popular",
            screenType = "tvshows",
            title = "Popular Shows",
            rowType = "popular",
            contentType = "shows",
            presentation = "landscape",
            defaultPosition = 1, position = 1
        )
    )

    val all = homeRows + moviesRows + tvShowsRows
}
```

### 3.4 Unified Base ViewModel

Create a shared base class that eliminates duplication across all content ViewModels:

```kotlin
abstract class BaseContentViewModel(
    protected val screenConfigRepository: ScreenConfigRepository,
    protected val contentLoader: ContentLoaderUseCase,
    protected val watchStatusRepository: WatchStatusRepository
) : ViewModel() {

    abstract val screenType: ScreenConfigRepository.ScreenType

    protected val rowStates = mutableListOf<ContentRowState>()
    
    private val _contentRows = MutableLiveData<List<ContentRow>>()
    val contentRows: LiveData<List<ContentRow>> = _contentRows

    private val _rowAppendEvents = MutableLiveData<RowAppendEvent>()
    val rowAppendEvents: LiveData<RowAppendEvent> = _rowAppendEvents

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _heroContent = MutableLiveData<ContentItem?>()
    val heroContent: LiveData<ContentItem?> = _heroContent

    init {
        viewModelScope.launch {
            screenConfigRepository.getRowsForScreen(screenType)
                .collect { configs -> buildRowsFromConfig(configs) }
        }
    }

    protected open fun buildRowsFromConfig(configs: List<RowConfigEntity>) {
        rowStates.clear()
        configs.forEach { config ->
            rowStates.add(config.toRowState())
        }
        loadAllRows()
    }

    protected fun loadAllRows() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load first row immediately for instant UI
            rowStates.firstOrNull()?.let { first ->
                loadRowContent(0, first, forceRefresh = false)
            }
            
            _isLoading.value = false
            
            // Stagger remaining rows
            rowStates.drop(1).forEachIndexed { idx, state ->
                launch {
                    delay(50L * (idx + 1))
                    loadRowContent(idx + 1, state, forceRefresh = false)
                }
            }
        }
    }

    protected suspend fun loadRowContent(
        rowIndex: Int,
        state: ContentRowState,
        forceRefresh: Boolean
    ) {
        val items = contentLoader.loadForRowType(
            rowType = state.rowType,
            contentType = state.contentType,
            page = 1,
            forceRefresh = forceRefresh
        )
        
        state.items.clear()
        state.items.addAll(items)
        state.currentPage = 1
        state.hasMore = items.size >= state.pageSize
        
        publishRows()
        
        if (_heroContent.value == null && items.isNotEmpty()) {
            _heroContent.value = items.first()
        }
    }

    fun requestNextPage(rowIndex: Int) {
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading || !state.hasMore) return

        viewModelScope.launch {
            state.isLoading = true
            val nextPage = state.currentPage + 1
            
            val newItems = contentLoader.loadForRowType(
                rowType = state.rowType,
                contentType = state.contentType,
                page = nextPage,
                forceRefresh = false
            )
            
            if (newItems.isNotEmpty()) {
                state.items.addAll(newItems)
                state.currentPage = nextPage
                state.hasMore = newItems.size >= state.pageSize
                _rowAppendEvents.value = RowAppendEvent(rowIndex, newItems)
            } else {
                state.hasMore = false
            }
            
            state.isLoading = false
        }
    }

    protected fun publishRows() {
        _contentRows.value = rowStates.map {
            ContentRow(
                title = it.title,
                items = it.items,
                presentation = it.presentation
            )
        }
    }

    fun updateHeroContent(item: ContentItem) {
        _heroContent.value = item
    }

    fun cleanupCache() {
        viewModelScope.launch {
            runCatching { contentLoader.cleanupCache() }
        }
    }
}

// Extension function to convert entity to row state
fun RowConfigEntity.toRowState(): ContentRowState {
    return ContentRowState(
        category = id,
        title = title,
        rowType = rowType,
        contentType = contentType,
        presentation = when (presentation) {
            "landscape" -> RowPresentation.LANDSCAPE_16_9
            else -> RowPresentation.PORTRAIT
        },
        pageSize = pageSize
    )
}
```

### 3.5 Simplified ViewModels

Now each screen's ViewModel becomes trivial:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    screenConfigRepository: ScreenConfigRepository,
    contentLoader: ContentLoaderUseCase,
    watchStatusRepository: WatchStatusRepository
) : BaseContentViewModel(screenConfigRepository, contentLoader, watchStatusRepository) {
    
    override val screenType = ScreenConfigRepository.ScreenType.HOME
}

@HiltViewModel
class MoviesViewModel @Inject constructor(
    screenConfigRepository: ScreenConfigRepository,
    contentLoader: ContentLoaderUseCase,
    watchStatusRepository: WatchStatusRepository
) : BaseContentViewModel(screenConfigRepository, contentLoader, watchStatusRepository) {
    
    override val screenType = ScreenConfigRepository.ScreenType.MOVIES
}

@HiltViewModel
class TvShowsViewModel @Inject constructor(
    screenConfigRepository: ScreenConfigRepository,
    contentLoader: ContentLoaderUseCase,
    watchStatusRepository: WatchStatusRepository
) : BaseContentViewModel(screenConfigRepository, contentLoader, watchStatusRepository) {
    
    override val screenType = ScreenConfigRepository.ScreenType.TV_SHOWS
}
```

---

## 4. Enhanced Configuration Schema

Your current HOME.json has a good foundation. Here's an enhanced version that supports all three screens and user customization:

```json
{
  "version": "2.0",
  "screens": {
    "home": {
      "rows": [
        {
          "id": "continue_watching",
          "title": "Continue Watching",
          "type": "continue_watching",
          "cardType": "landscape",
          "order": 1,
          "enabled": true,
          "userConfigurable": true,
          "systemRow": true,
          "requiresAuth": true,
          "displayOptions": {
            "showProgress": true,
            "showEpisodeInfo": true
          }
        },
        {
          "id": "networks",
          "title": "Networks",
          "type": "networks",
          "cardType": "landscape",
          "order": 2,
          "enabled": true,
          "userConfigurable": true
        },
        {
          "id": "franchises",
          "title": "Franchises",
          "type": "collections",
          "cardType": "portrait",
          "order": 3,
          "enabled": true,
          "userConfigurable": true
        },
        {
          "id": "directors",
          "title": "Directors",
          "type": "directors",
          "cardType": "landscape",
          "order": 4,
          "enabled": true,
          "userConfigurable": true
        }
      ]
    },
    "movies": {
      "rows": [
        {
          "id": "trending",
          "title": "Trending Movies",
          "type": "trakt_list",
          "cardType": "portrait",
          "order": 1,
          "enabled": true,
          "systemRow": true,
          "dataSource": {
            "kind": "movies",
            "list_type": "builtin",
            "slug": "trending"
          }
        },
        {
          "id": "popular",
          "title": "Popular Movies",
          "type": "trakt_list",
          "cardType": "portrait",
          "order": 2,
          "enabled": true,
          "dataSource": {
            "kind": "movies",
            "list_type": "builtin",
            "slug": "popular"
          }
        },
        {
          "id": "4k_releases",
          "title": "Latest 4K Releases",
          "type": "trakt_list",
          "cardType": "portrait",
          "order": 3,
          "enabled": true,
          "dataSource": {
            "kind": "movies",
            "list_type": "custom",
            "url": "https://trakt.tv/users/example/lists/4k-releases"
          }
        }
      ]
    },
    "tvshows": {
      "rows": [
        {
          "id": "trending",
          "title": "Trending Shows",
          "type": "trakt_list",
          "cardType": "landscape",
          "order": 1,
          "enabled": true,
          "systemRow": true,
          "dataSource": {
            "kind": "shows",
            "list_type": "builtin",
            "slug": "trending"
          }
        },
        {
          "id": "popular",
          "title": "Popular Shows",
          "type": "trakt_list",
          "cardType": "landscape",
          "order": 2,
          "enabled": true,
          "dataSource": {
            "kind": "shows",
            "list_type": "builtin",
            "slug": "popular"
          }
        }
      ]
    }
  },
  "collections": [
    {
      "id": "007",
      "name": "007",
      "backgroundImageUrl": "drawable://collection_007",
      "dataUrl": "https://trakt.tv/users/proturbo18/lists/007?sort=released,desc"
    }
  ],
  "directors": [
    {
      "id": "nolan",
      "name": "Christopher Nolan",
      "backgroundImageUrl": "drawable://director_nolan",
      "dataUrl": "https://trakt.tv/users/acox03/lists/christopher-nolan?sort=rank,asc"
    }
  ],
  "networks": [
    {
      "id": "Netflix",
      "backgroundImageUrl": "drawable://network_netflix",
      "dataUrl": "https://trakt.tv/users/lhuss13/lists/netflix-movies?sort=released,asc"
    }
  ]
}
```

---

## 5. Settings UI for Row Customization

### 5.1 Add to SettingsActivity

Add a new submenu item in SettingsActivity:

```kotlin
private val submenuItems = listOf(
    SubmenuItem("accounts", "Accounts", R.drawable.ic_user, "Manage your accounts preferences"),
    SubmenuItem(
        id = "layout",
        label = "Layout & Rows",
        iconRes = R.drawable.ic_layout,
        description = "Customize row visibility and order"
    ),
    SubmenuItem("resolving", "Link Resolving", R.drawable.ic_link, "Configure link resolution settings"),
    // ... other items
)
```

### 5.2 RowCustomizationFragment

```kotlin
@AndroidEntryPoint
class RowCustomizationFragment : Fragment() {
    
    private val viewModel: RowCustomizationViewModel by viewModels()
    private lateinit var binding: FragmentRowCustomizationBinding
    private lateinit var adapter: RowConfigAdapter
    
    private var currentScreen = ScreenConfigRepository.ScreenType.HOME
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRowCustomizationBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        setupResetButton()
        observeViewModel()
    }
    
    private fun setupTabs() {
        binding.tabHome.setOnClickListener { 
            switchScreen(ScreenConfigRepository.ScreenType.HOME) 
        }
        binding.tabMovies.setOnClickListener { 
            switchScreen(ScreenConfigRepository.ScreenType.MOVIES) 
        }
        binding.tabTvShows.setOnClickListener { 
            switchScreen(ScreenConfigRepository.ScreenType.TV_SHOWS) 
        }
    }
    
    private fun switchScreen(screen: ScreenConfigRepository.ScreenType) {
        currentScreen = screen
        viewModel.loadRowsForScreen(screen)
        updateTabSelection()
    }
    
    private fun setupRecyclerView() {
        adapter = RowConfigAdapter(
            onToggleVisibility = { row -> viewModel.toggleRowVisibility(row) },
            onMoveUp = { row -> viewModel.moveRowUp(row) },
            onMoveDown = { row -> viewModel.moveRowDown(row) }
        )
        
        binding.rowsList.adapter = adapter
        binding.rowsList.layoutManager = LinearLayoutManager(requireContext())
        
        // Optional: Add ItemTouchHelper for drag-and-drop reordering
        val touchHelper = ItemTouchHelper(RowDragCallback { from, to ->
            viewModel.swapRows(from, to)
        })
        touchHelper.attachToRecyclerView(binding.rowsList)
    }
    
    private fun setupResetButton() {
        binding.btnResetDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset to Defaults")
                .setMessage("This will reset all row settings for ${currentScreen.name} to their defaults.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetToDefaults(currentScreen)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun observeViewModel() {
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            adapter.submitList(rows)
        }
    }
}
```

### 5.3 RowCustomizationViewModel

```kotlin
@HiltViewModel
class RowCustomizationViewModel @Inject constructor(
    private val screenConfigRepository: ScreenConfigRepository
) : ViewModel() {
    
    private val _rows = MutableLiveData<List<RowConfigEntity>>()
    val rows: LiveData<List<RowConfigEntity>> = _rows
    
    private var currentScreen = ScreenConfigRepository.ScreenType.HOME
    
    init {
        loadRowsForScreen(currentScreen)
    }
    
    fun loadRowsForScreen(screen: ScreenConfigRepository.ScreenType) {
        currentScreen = screen
        viewModelScope.launch {
            screenConfigRepository.getAllRowsForSettings(screen)
                .collect { _rows.value = it }
        }
    }
    
    fun toggleRowVisibility(row: RowConfigEntity) {
        viewModelScope.launch {
            screenConfigRepository.toggleRowVisibility(row.id, !row.enabled)
        }
    }
    
    fun moveRowUp(row: RowConfigEntity) {
        val currentList = _rows.value ?: return
        val index = currentList.indexOf(row)
        if (index > 0) {
            swapRows(index, index - 1)
        }
    }
    
    fun moveRowDown(row: RowConfigEntity) {
        val currentList = _rows.value ?: return
        val index = currentList.indexOf(row)
        if (index < currentList.size - 1) {
            swapRows(index, index + 1)
        }
    }
    
    fun swapRows(fromIndex: Int, toIndex: Int) {
        val currentList = _rows.value?.toMutableList() ?: return
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        viewModelScope.launch {
            val fromRow = currentList[fromIndex]
            val toRow = currentList[toIndex]
            
            screenConfigRepository.reorderRow(fromRow.id, toIndex)
            screenConfigRepository.reorderRow(toRow.id, fromIndex)
        }
    }
    
    fun resetToDefaults(screen: ScreenConfigRepository.ScreenType) {
        viewModelScope.launch {
            screenConfigRepository.resetScreenToDefaults(screen)
        }
    }
}
```

### 5.4 Layout XML (fragment_row_customization.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <!-- Tab Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp">

        <Button
            android:id="@+id/tab_home"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Home" />

        <Button
            android:id="@+id/tab_movies"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Movies" />

        <Button
            android:id="@+id/tab_tv_shows"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="TV Shows" />
    </LinearLayout>

    <!-- Rows List -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rows_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <!-- Reset Button -->
    <Button
        android:id="@+id/btn_reset_defaults"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginTop="16dp"
        android:text="Reset to Defaults" />

</LinearLayout>
```

---

## 6. Implementation Roadmap

### Phase 1: Database Schema (Week 1)

- [ ] Create `RowConfigEntity.kt` in `data/local/`
- [ ] Create `RowConfigDao.kt` in `data/local/dao/`
- [ ] Add entity to `@Database` annotation in `AppDatabase.kt`
- [ ] Add `rowConfigDao()` abstract method to `AppDatabase`
- [ ] Increment database version to 12
- [ ] Create migration from version 11 to 12
- [ ] Create `DefaultRowConfigs.kt` object with all default rows
- [ ] Add database initialization call in `Test1App.onCreate()`
- [ ] Write unit tests for RowConfigDao

### Phase 2: Repository Layer (Week 1-2)

- [ ] Create `ScreenConfigRepository.kt`
- [ ] Add `ScreenType` enum with HOME, MOVIES, TV_SHOWS
- [ ] Implement `getRowsForScreen()` method
- [ ] Implement `getAllRowsForSettings()` method  
- [ ] Implement `toggleRowVisibility()` method
- [ ] Implement `reorderRow()` method
- [ ] Implement `resetScreenToDefaults()` method
- [ ] Implement `initializeDefaults()` method
- [ ] Add Hilt `@Provides` binding in `AppModule.kt`
- [ ] Migrate logic from `HomeConfigRepository` (if keeping JSON as fallback)
- [ ] Write unit tests for ScreenConfigRepository
- [ ] Delete or deprecate `HomeConfigRepository`

### Phase 3: ViewModel Refactor (Week 2-3)

- [ ] Create `ContentRowState.kt` data class (shared version)
- [ ] Create `ContentLoaderUseCase.kt` for unified content loading
- [ ] Create `BaseContentViewModel.kt` abstract class
- [ ] Implement shared `buildRowsFromConfig()` method
- [ ] Implement shared `loadAllRows()` method
- [ ] Implement shared `loadRowContent()` method
- [ ] Implement shared `requestNextPage()` method
- [ ] Implement shared `publishRows()` method
- [ ] Create `RowConfigEntity.toRowState()` extension function
- [ ] Refactor `HomeViewModel` to extend `BaseContentViewModel`
- [ ] Remove duplicated code from `HomeViewModel`
- [ ] Refactor `MoviesViewModel` to extend `BaseContentViewModel`
- [ ] Remove all hardcoded row definitions from `MoviesViewModel`
- [ ] Refactor `TvShowsViewModel` to extend `BaseContentViewModel`
- [ ] Remove all hardcoded row definitions from `TvShowsViewModel`
- [ ] Update Hilt module for new ViewModel dependencies
- [ ] Write unit tests for `BaseContentViewModel`
- [ ] Write unit tests for refactored ViewModels
- [ ] Delete any remaining duplicated code

### Phase 4: Settings UI (Week 3-4)

- [ ] Create `fragment_row_customization.xml` layout
- [ ] Create `item_row_config.xml` for list items
- [ ] Create `RowConfigAdapter.kt` RecyclerView adapter
- [ ] Create `RowCustomizationFragment.kt`
- [ ] Create `RowCustomizationViewModel.kt`
- [ ] Add "Layout & Rows" item to `SettingsActivity` submenu
- [ ] Wire up fragment loading in `SettingsActivity.loadFragment()`
- [ ] Implement tab switching (Home/Movies/TV Shows)
- [ ] Implement visibility toggle switches
- [ ] Create `RowDragCallback.kt` for ItemTouchHelper
- [ ] Implement drag-and-drop reordering
- [ ] Implement move up/down buttons (TV remote friendly)
- [ ] Implement "Reset to Defaults" dialog and functionality
- [ ] Add TV-friendly focus handling for all controls
- [ ] Style the UI to match existing Settings design
- [ ] Test with D-pad navigation

### Phase 5: Testing & Polish (Week 4)

- [ ] Integration test: Row visibility changes reflect on Home screen
- [ ] Integration test: Row visibility changes reflect on Movies screen
- [ ] Integration test: Row visibility changes reflect on TV Shows screen
- [ ] Integration test: Row reordering persists across app restart
- [ ] Integration test: Reset to defaults works correctly
- [ ] Edge case test: No authenticated user (requiresAuth rows)
- [ ] Edge case test: Empty row (no content available)
- [ ] Edge case test: All rows disabled
- [ ] Performance test: Many rows (20+) on single screen
- [ ] Performance test: Rapid row toggling
- [ ] Memory profiling: No leaks from config changes
- [ ] UI/UX review: Focus states are clear on TV
- [ ] UI/UX review: Animations feel responsive
- [ ] Fix any discovered bugs
- [ ] Code review and cleanup
- [ ] Update any affected documentation

---

## 7. Additional Optimization Opportunities

### 7.1 Content Loading Optimization

- **Current:** Each row loads independently with separate API calls
- **Improvement:** Create `ContentLoaderUseCase` that batches API calls and shares cache

```kotlin
@Singleton
class ContentLoaderUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val contentRepository: ContentRepository,
    private val continueWatchingRepository: ContinueWatchingRepository
) {
    suspend fun loadForRowType(
        rowType: String,
        contentType: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<ContentItem> {
        return when (rowType) {
            "trending" -> loadTrending(contentType, page, forceRefresh)
            "popular" -> loadPopular(contentType, page, forceRefresh)
            "continue_watching" -> loadContinueWatching(forceRefresh)
            "networks" -> loadNetworks()
            "collections" -> loadCollections()
            "directors" -> loadDirectors()
            else -> emptyList()
        }
    }
    
    private suspend fun loadTrending(
        contentType: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<ContentItem> {
        val resource = when (contentType) {
            "movies" -> mediaRepository.getTrendingMovies(page).first { it !is Resource.Loading }
            "shows" -> mediaRepository.getTrendingShows(page).first { it !is Resource.Loading }
            else -> return emptyList()
        }
        return when (resource) {
            is Resource.Success -> resource.data
            is Resource.Error -> resource.cachedData ?: emptyList()
            is Resource.Loading -> resource.cachedData ?: emptyList()
        }
    }
    
    // Similar implementations for other row types...
}
```

### 7.2 Prefetching Strategy

Your current `RowPrefetchManager` is good. Enhance it to:

- Prefetch based on row configuration priority
- Use user scroll velocity to predict which rows to prefetch
- Cache more aggressively for system rows

```kotlin
@Singleton
class EnhancedRowPrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenConfigRepository: ScreenConfigRepository
) {
    private val prefetchedRows = mutableSetOf<String>()
    
    fun onRowFocused(currentRowId: String, screenType: ScreenConfigRepository.ScreenType) {
        viewModelScope.launch {
            val allRows = screenConfigRepository.getRowsForScreen(screenType).first()
            val currentIndex = allRows.indexOfFirst { it.id == currentRowId }
            
            // Prefetch adjacent rows
            listOf(currentIndex - 1, currentIndex + 1, currentIndex + 2).forEach { idx ->
                allRows.getOrNull(idx)?.let { row ->
                    if (prefetchedRows.add(row.id)) {
                        prefetchRowContent(row)
                    }
                }
            }
        }
    }
    
    private suspend fun prefetchRowContent(row: RowConfigEntity) {
        // Implementation based on row type
    }
}
```

### 7.3 Memory Management

Your `AccentColorCache` with `LruCache(200)` is good. Additional improvements:

- Implement row-level recycling for off-screen content
- Add memory pressure monitoring to reduce cache sizes
- Consider Paging 3 for infinite scroll rows

```kotlin
// Add to BaseContentViewModel
private val memoryMonitor = object : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Clear non-visible row content
                rowStates.forEachIndexed { index, state ->
                    if (index > 2) { // Keep first 3 rows
                        state.items.clear()
                        state.currentPage = 0
                        state.hasMore = true
                    }
                }
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() { onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE) }
}
```

---

## 8. Summary of Required File Changes

| File | Action |
|------|--------|
| `data/local/RowConfigEntity.kt` | CREATE - New database entity |
| `data/local/dao/RowConfigDao.kt` | CREATE - New DAO interface |
| `data/local/AppDatabase.kt` | MODIFY - Add entity and DAO, version 12 |
| `data/config/DefaultRowConfigs.kt` | CREATE - Default row definitions |
| `data/repository/ScreenConfigRepository.kt` | CREATE - Unified config repository |
| `domain/ContentLoaderUseCase.kt` | CREATE - Unified content loading |
| `ui/base/BaseContentViewModel.kt` | CREATE - Shared ViewModel base class |
| `ui/base/ContentRowState.kt` | CREATE - Shared row state data class |
| `ui/home/HomeViewModel.kt` | REFACTOR - Extend base class, remove duplication |
| `ui/movies/MoviesViewModel.kt` | REFACTOR - Extend base class, remove hardcoding |
| `ui/tvshows/TvShowsViewModel.kt` | REFACTOR - Extend base class, remove hardcoding |
| `ui/settings/RowCustomizationFragment.kt` | CREATE - Settings UI fragment |
| `ui/settings/RowCustomizationViewModel.kt` | CREATE - Settings ViewModel |
| `ui/settings/adapter/RowConfigAdapter.kt` | CREATE - RecyclerView adapter |
| `ui/settings/SettingsActivity.kt` | MODIFY - Add new submenu item |
| `res/layout/fragment_row_customization.xml` | CREATE - Settings layout |
| `res/layout/item_row_config.xml` | CREATE - List item layout |
| `di/AppModule.kt` | MODIFY - Add new bindings |
| `data/repository/HomeConfigRepository.kt` | DELETE or DEPRECATE |

---

## 9. Conclusion

This architecture overhaul will transform your TV app from a hardcoded, rigid structure to a flexible, user-customizable experience. The key benefits are:

- **Single Source of Truth:** All row configurations live in the database, not scattered across ViewModels
- **Code Reduction:** Estimated 40% reduction in ViewModel code through shared base class
- **User Empowerment:** Users can customize their experience via Settings
- **Future-Proofing:** Adding new row types requires only database entries, not code changes
- **Maintainability:** Changes to row behavior happen in one place, not three ViewModels

*The estimated implementation time is 4 weeks for a single developer, with the database and repository layers being the most critical path items.*

---

## Appendix: Migration Script

For the database migration from version 11 to 12:

```kotlin
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS row_config (
                id TEXT PRIMARY KEY NOT NULL,
                screenType TEXT NOT NULL,
                title TEXT NOT NULL,
                rowType TEXT NOT NULL,
                contentType TEXT,
                presentation TEXT NOT NULL,
                dataSourceUrl TEXT,
                defaultPosition INTEGER NOT NULL,
                position INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                requiresAuth INTEGER NOT NULL DEFAULT 0,
                pageSize INTEGER NOT NULL DEFAULT 20,
                isSystemRow INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_row_config_screenType_position 
            ON row_config (screenType, position)
        """)
    }
}
```

Add to `AppModule.kt`:

```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "test1_database"
    )
    .addMigrations(MIGRATION_11_12)
    .build()
}
```
