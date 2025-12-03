# TODO

## High Priority - Core Functionality

### In-App Trakt List Viewing
- [ ] **Parse Trakt URL → extract username & list ID**
  - Example: `https://trakt.tv/users/goingnineteen/lists/mcu` → `username="goingnineteen", list="mcu"`
  - Use regex: `Regex("trakt\\.tv/users/([^/]+)/lists/([^/?]+)")`

- [ ] **Create TraktListActivity/Fragment**
  - Similar to TraktMediaActivity
  - Accept username, listId, and title as intent extras
  - Call `TraktApiService.getListMovies()` or `getListShows()` to fetch content
  - Display content in rows (reuse ContentRowAdapter)

- [ ] **Update click handler in HomeFragment**
  - Replace browser redirect with in-app activity launch
  - Handle both movies and shows lists
  - Show loading state while fetching

- [ ] **Implement click navigation for all static rows**
  - Collections (Harry Potter, Marvel, Star Wars, etc.)
  - Directors (Nolan, Spielberg, Scorsese, etc.)
  - Networks (Netflix, Disney+, HBO Max, etc.)
  - All should navigate to TraktListActivity and show content

### Authentication-Gated Features
- [ ] **Implement 'My Trakt' row**
  - Only show after user authorizes Trakt
  - Fetch user's liked lists from Trakt API: `GET /users/me/lists`
  - Dynamically create cards for each liked list
  - Use `trakt_likedlist.png` as background image
  - Show list name as title overlay

- [ ] **Hide Continue Watching until Trakt auth**
  - Check `traktAuthRepository.isAuthenticated()`
  - Only load Continue Watching row if authenticated
  - Show placeholder card to prompt authentication if not authenticated

## Medium Priority - Polish & UX

### Visual Assets
- [ ] **Add custom Trakt drawable images** (when you get them)
  - `trakt_likedlist.png` to `app/src/main/res/drawable/`
  - `trakt_watchlist.png` to `app/src/main/res/drawable/`
  - Update StaticRowData.kt lines 308, 315, 322, 329 to use new drawables

### Focus & Animation Improvements
- [ ] **Extract accent colors from drawable resources**
  - Currently using DEFAULT_BORDER_COLOR (white) for all drawable items
  - Extract dominant color from collection/director/network images
  - Apply to focus border glow (PosterAdapter.kt:151)

- [ ] **Improve title overlay positioning for Trakt cards**
  - Consider landscape orientation for better text readability
  - Or use semi-transparent background behind text
  - Test on TV to ensure readability from couch distance

## Low Priority - Future Enhancements

### Dynamic Content
- [ ] **Fetch network content from actual streaming services**
  - Currently using Trakt lists (movies available on Netflix, etc.)
  - Could integrate with JustWatch API or similar for real-time availability
  - Would require additional API integration

- [ ] **Add "More" option to expand collection/director rows**
  - Collections row shows 23 items - might be overwhelming
  - Consider showing top 10 by default with "See All Collections" card at end
  - Same for Directors row (currently 10 items)

### Settings Integration
- [ ] **Allow customizing which static rows appear**
  - Add Collections, Directors, Networks to RowConfig database
  - Let user hide/reorder these rows in Settings
  - Currently they're hardcoded in DefaultRowConfigs

### Performance
- [ ] **Optimize drawable loading for large images**
  - Some network logos might be large (AVIF/JPG format)
  - Consider converting to WebP for smaller file size
  - Or use Glide to load drawables for automatic optimization

## Technical Debt

### Code Quality
- [ ] **Implement nameDisplayMode field properly**
  - Currently defined in data classes but not used
  - Either implement it or remove from data model
  - Current implementation uses `tmdbId < 0` check instead

- [ ] **Fix ignored Robolectric tests** (Low priority)
  - 2 tests in RowConfigDaoTest and ScreenConfigRepositoryTest
  - Issue: Backtick method names with `= runBlocking` syntax
  - Covered by 49 passing integration/edge-case/performance tests
  - See Phase 5 test coverage documentation

---

## Completed ✅

- [x] **Network, Collection, and Director rows enlarge 1.1x on focus**
  - Fixed: Removed early return in PosterAdapter drawable handling (line 157)
  - Focus listener now properly attached to all items

- [x] **Implement drawable:// URL scheme for static resources**
  - PosterAdapter detects "drawable://" and loads via getIdentifier()
  - Used for Collections, Directors, Networks

- [x] **Create StaticRowData.kt with all static content**
  - 23 Collections (Marvel, DC, Star Wars, etc.)
  - 10 Directors (Nolan, Spielberg, etc.)
  - 6 Networks (Netflix, Disney+, etc.)
  - 4 Trakt Library items (Collection/Watchlist for Movies/TV)

- [x] **Implement loadNetworks(), loadCollections(), loadDirectors() in ContentLoaderUseCase**
  - Converts StaticRowData items to ContentItem instances
  - Uses tmdbId = -1 to mark as special items
  - Stores Trakt URL in imdbId field for navigation

- [x] **Add title overlays to all special items**
  - Items with tmdbId < 0 automatically show titles
  - Trakt library cards show: "Movie Collection", "Movie Watchlist", etc.

- [x] **Build system and test infrastructure**
  - Phase 5 complete: 49 tests passing
  - 7 Integration tests
  - 16 Edge case tests
  - 11 Performance tests

- [x] **Basic click navigation (browser redirect)**
  - Collections/Directors/Networks open Trakt list in browser
  - Placeholder for in-app viewing (to be implemented above)

---

## Notes

**Test Coverage**: 49/51 tests passing (96% pass rate)
- 2 pre-existing Robolectric tests ignored due to configuration issues
- Functionality fully covered by working integration tests

**Architecture Decision**:
- Static rows (Collections, Directors, Networks) use drawable resources with "drawable://" URL scheme
- Dynamic rows (Trending, Popular, Continue Watching) use TMDB/Trakt API with Glide image loading
- This hybrid approach optimizes for both visual appeal (custom images) and dynamic content

**Next User Test**: Deploy to Android TV and verify:
1. ✅ Collections/Directors/Networks rows appear with images
2. ✅ All rows scale 1.1x on focus with colored border
3. ✅ Titles show on Trakt library cards
4. ⚠️  Clicking currently opens browser (implement in-app viewing next)
