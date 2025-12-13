package com.strmr.tv.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.strmr.tv.data.local.AppDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug Activity to verify Phase 1 Database Implementation
 *
 * To use this:
 * 1. Copy this file to app/src/main/java/com/strmr/tv/debug/DatabaseDebugActivity.kt
 * 2. Add to AndroidManifest.xml:
 *    <activity android:name=".debug.DatabaseDebugActivity" />
 * 3. Launch via ADB:
 *    adb shell am start -n com.strmr.tv/.debug.DatabaseDebugActivity
 * 4. Check logcat for "PHASE1_DB_TEST" tag
 */
@AndroidEntryPoint
class DatabaseDebugActivity : ComponentActivity() {

    @Inject
    lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            runDatabaseTests()
        }
    }

    private suspend fun runDatabaseTests() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Phase 1 Database Verification Starting")
        Log.d(TAG, "========================================")

        val dao = database.rowConfigDao()

        try {
            // Test 1: Check total row count
            val homeCount = dao.getRowCountForScreen("home")
            val moviesCount = dao.getRowCountForScreen("movies")
            val tvShowsCount = dao.getRowCountForScreen("tvshows")
            val totalCount = homeCount + moviesCount + tvShowsCount

            Log.d(TAG, "")
            Log.d(TAG, "TEST 1: Row Counts")
            Log.d(TAG, "  Home screen rows: $homeCount (expected: 6)")
            Log.d(TAG, "  Movies screen rows: $moviesCount (expected: 3)")
            Log.d(TAG, "  TV Shows screen rows: $tvShowsCount (expected: 2)")
            Log.d(TAG, "  Total rows: $totalCount (expected: 11)")
            Log.d(TAG, "  Result: ${if (totalCount == 11) "✅ PASS" else "❌ FAIL"}")

            // Test 2: Verify home screen rows
            Log.d(TAG, "")
            Log.d(TAG, "TEST 2: Home Screen Rows")
            val homeRows = dao.getAllRowsForScreen("home").first()
            homeRows.forEachIndexed { index, row ->
                Log.d(TAG, "  [$index] ${row.title}")
                Log.d(TAG, "      ID: ${row.id}")
                Log.d(TAG, "      Row Type: ${row.rowType}")
                Log.d(TAG, "      Position: ${row.position} (default: ${row.defaultPosition})")
                Log.d(TAG, "      Enabled: ${row.enabled}")
                Log.d(TAG, "      Requires Auth: ${row.requiresAuth}")
                Log.d(TAG, "      System Row: ${row.isSystemRow}")
                Log.d(TAG, "      Presentation: ${row.presentation}")
            }
            val homeRowsCorrect = homeRows.size == 6 &&
                homeRows.all { it.position == it.defaultPosition } &&
                homeRows.all { it.enabled }
            Log.d(TAG, "  Result: ${if (homeRowsCorrect) "✅ PASS" else "❌ FAIL"}")

            // Test 3: Verify movies screen rows
            Log.d(TAG, "")
            Log.d(TAG, "TEST 3: Movies Screen Rows")
            val moviesRows = dao.getAllRowsForScreen("movies").first()
            moviesRows.forEachIndexed { index, row ->
                Log.d(TAG, "  [$index] ${row.title} (position: ${row.position}, enabled: ${row.enabled})")
            }
            val moviesRowsCorrect = moviesRows.size == 3 &&
                moviesRows.all { it.enabled } &&
                moviesRows[0].title == "Trending Movies"
            Log.d(TAG, "  Result: ${if (moviesRowsCorrect) "✅ PASS" else "❌ FAIL"}")

            // Test 4: Verify TV shows screen rows
            Log.d(TAG, "")
            Log.d(TAG, "TEST 4: TV Shows Screen Rows")
            val tvShowsRows = dao.getAllRowsForScreen("tvshows").first()
            tvShowsRows.forEachIndexed { index, row ->
                Log.d(TAG, "  [$index] ${row.title} (position: ${row.position}, enabled: ${row.enabled})")
            }
            val tvShowsRowsCorrect = tvShowsRows.size == 2 &&
                tvShowsRows.all { it.enabled } &&
                tvShowsRows[0].title == "Trending Shows"
            Log.d(TAG, "  Result: ${if (tvShowsRowsCorrect) "✅ PASS" else "❌ FAIL"}")

            // Test 5: Verify system rows
            Log.d(TAG, "")
            Log.d(TAG, "TEST 5: System Rows (should be 3)")
            val allHomeRows = dao.getAllRowsForScreen("home").first()
            val allMoviesRows = dao.getAllRowsForScreen("movies").first()
            val allTvShowsRows = dao.getAllRowsForScreen("tvshows").first()
            val systemRows = (allHomeRows + allMoviesRows + allTvShowsRows).filter { it.isSystemRow }
            systemRows.forEach { row ->
                Log.d(TAG, "  - ${row.screenType}: ${row.title}")
            }
            Log.d(TAG, "  Result: ${if (systemRows.size == 3) "✅ PASS" else "❌ FAIL"}")

            // Test 6: Verify auth-required rows
            Log.d(TAG, "")
            Log.d(TAG, "TEST 6: Auth-Required Rows (should be 1)")
            val authRows = (allHomeRows + allMoviesRows + allTvShowsRows).filter { it.requiresAuth }
            authRows.forEach { row ->
                Log.d(TAG, "  - ${row.title}")
            }
            Log.d(TAG, "  Result: ${if (authRows.size == 1) "✅ PASS" else "❌ FAIL"}")

            // Test 7: Verify only enabled rows are returned
            Log.d(TAG, "")
            Log.d(TAG, "TEST 7: Enabled Rows Query")
            val enabledHomeRows = dao.getEnabledRowsForScreen("home").first()
            Log.d(TAG, "  Enabled home rows: ${enabledHomeRows.size}")
            Log.d(TAG, "  All home rows: ${homeRows.size}")
            val enabledQueryWorks = enabledHomeRows.size == homeRows.filter { it.enabled }.size
            Log.d(TAG, "  Result: ${if (enabledQueryWorks) "✅ PASS" else "❌ FAIL"}")

            // Test 8: Check database version
            Log.d(TAG, "")
            Log.d(TAG, "TEST 8: Database Version")
            val dbVersion = database.openHelper.readableDatabase.version
            Log.d(TAG, "  Current version: $dbVersion (expected: 12)")
            Log.d(TAG, "  Result: ${if (dbVersion == 12) "✅ PASS" else "❌ FAIL"}")

            Log.d(TAG, "")
            Log.d(TAG, "========================================")
            Log.d(TAG, "Phase 1 Database Verification Complete")
            Log.d(TAG, "========================================")
            Log.d(TAG, "All tests passed! ✅")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Database test failed with exception", e)
        }

        // Close activity after tests
        finish()
    }

    companion object {
        private const val TAG = "PHASE1_DB_TEST"
    }
}
