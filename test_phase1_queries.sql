-- Phase 1 Database Verification Queries
-- Copy and paste these into Android Studio's Database Inspector
-- (View > Tool Windows > App Inspection > Database Inspector)

-- ========================================
-- TEST 1: Database Migration Success
-- ========================================

-- 1.1: Verify row_config table exists
SELECT name FROM sqlite_master
WHERE type='table' AND name='row_config';
-- Expected: Should return 'row_config'

-- 1.2: Check table schema (all columns present)
PRAGMA table_info(row_config);
-- Expected: Should show 13 columns:
-- id, screenType, title, rowType, contentType, presentation,
-- dataSourceUrl, defaultPosition, position, enabled,
-- requiresAuth, pageSize, isSystemRow

-- 1.3: Verify index exists
SELECT name FROM sqlite_master
WHERE type='index' AND tbl_name='row_config';
-- Expected: Should return 'index_row_config_screenType_position'

-- 1.4: Check database version
PRAGMA user_version;
-- Expected: Should return 12

-- ========================================
-- TEST 2: Default Row Initialization
-- ========================================

-- 2.1: Count total rows
SELECT COUNT(*) as total_rows FROM row_config;
-- Expected: 11 rows total

-- 2.2: Count rows by screen type
SELECT screenType, COUNT(*) as count
FROM row_config
GROUP BY screenType
ORDER BY screenType;
-- Expected:
--   home: 6
--   movies: 3
--   tvshows: 2

-- 2.3: Verify all rows are enabled by default
SELECT screenType, SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count
FROM row_config
GROUP BY screenType;
-- Expected: All counts should match total counts from 2.2

-- 2.4: View all home screen rows (detailed)
SELECT
    id,
    title,
    rowType,
    contentType,
    presentation,
    position,
    defaultPosition,
    enabled,
    requiresAuth,
    isSystemRow
FROM row_config
WHERE screenType = 'home'
ORDER BY position;
-- Expected: 6 rows in order:
--   1. Continue Watching (position 0, requiresAuth=1, isSystemRow=1)
--   2. Networks (position 1)
--   3. Franchises (position 2)
--   4. Directors (position 3)
--   5. Trending Movies (position 4)
--   6. Trending Shows (position 5)

-- 2.5: View all movies screen rows
SELECT id, title, rowType, position, enabled, isSystemRow
FROM row_config
WHERE screenType = 'movies'
ORDER BY position;
-- Expected: 3 rows:
--   1. Trending Movies (position 0, isSystemRow=1)
--   2. Popular Movies (position 1)
--   3. Latest 4K Releases (position 2)

-- 2.6: View all TV shows screen rows
SELECT id, title, rowType, position, enabled
FROM row_config
WHERE screenType = 'tvshows'
ORDER BY position;
-- Expected: 2 rows:
--   1. Trending Shows (position 0, isSystemRow=1)
--   2. Popular Shows (position 1)

-- 2.7: Verify position matches defaultPosition for all rows
SELECT id, title, position, defaultPosition,
    CASE WHEN position = defaultPosition THEN 'OK' ELSE 'MISMATCH' END as status
FROM row_config
ORDER BY screenType, position;
-- Expected: All rows should show 'OK'

-- ========================================
-- Additional Verification Queries
-- ========================================

-- Check for system rows (should be 3 total)
SELECT screenType, id, title
FROM row_config
WHERE isSystemRow = 1;
-- Expected:
--   home: home_continue_watching
--   movies: movies_trending
--   tvshows: tvshows_trending

-- Check for rows requiring authentication (should be 1 total)
SELECT id, title
FROM row_config
WHERE requiresAuth = 1;
-- Expected: home_continue_watching

-- Verify no data loss in other tables
SELECT
    'cached_content' as table_name, COUNT(*) as row_count FROM cached_content
UNION ALL
SELECT 'trakt_account', COUNT(*) FROM trakt_account
UNION ALL
SELECT 'trakt_user_item', COUNT(*) FROM trakt_user_item
UNION ALL
SELECT 'continue_watching', COUNT(*) FROM continue_watching
UNION ALL
SELECT 'watch_status', COUNT(*) FROM watch_status;
-- Expected: Should show counts matching your existing data
-- (not 0, which would indicate data loss)
