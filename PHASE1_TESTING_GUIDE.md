# Phase 1 Testing Guide

This guide explains how to verify the Phase 1 database schema implementation.

## Quick Testing Methods

### Method 1: ADB Shell Script (Recommended)
This is the fastest way to verify the database.

1. Make the script executable:
```bash
chmod +x test_phase1_queries.sh
```

2. Connect your device/emulator and run:
```bash
./test_phase1_queries.sh
```

3. Review the output:
   - Database version should be **12**
   - Total rows should be **11**
   - Home screen: **6 rows**
   - Movies screen: **3 rows**
   - TV Shows screen: **2 rows**

### Method 2: Android Studio Database Inspector (Visual)
Best for visual inspection and manual queries.

1. **Run the app** on a device/emulator
2. Open **View → Tool Windows → App Inspection**
3. Select **Database Inspector** tab
4. Select your app process and **test1_tv_database**
5. You should see the **row_config** table in the list
6. Click on **row_config** to view all rows

#### Run Custom Queries:
1. Click the **"Query"** tab in Database Inspector
2. Copy queries from `test_phase1_queries.sql`
3. Paste and execute them one by one
4. Verify results match expected values (comments in SQL file)

### Method 3: In-App Debug Activity (Programmatic)
Best for automated verification during development.

1. Copy the example activity:
```bash
mkdir -p app/src/main/java/com/test1/tv/debug
cp DatabaseDebugActivity.kt.example app/src/main/java/com/test1/tv/debug/DatabaseDebugActivity.kt
```

2. Add to `AndroidManifest.xml` (inside `<application>` tag):
```xml
<activity android:name=".debug.DatabaseDebugActivity" />
```

3. Rebuild and install the app

4. Launch the debug activity:
```bash
adb shell am start -n com.test1.tv/.debug.DatabaseDebugActivity
```

5. Check logcat:
```bash
adb logcat -s PHASE1_DB_TEST
```

You should see 8 tests all showing ✅ PASS.

## Expected Results Summary

### Database Structure
- ✅ Database version: **12**
- ✅ Table `row_config` exists with 13 columns
- ✅ Index `index_row_config_screenType_position` exists

### Default Rows
| Screen Type | Row Count | System Rows | Auth Required |
|-------------|-----------|-------------|---------------|
| home        | 6         | 1           | 1             |
| movies      | 3         | 1           | 0             |
| tvshows     | 2         | 1           | 0             |
| **Total**   | **11**    | **3**       | **1**         |

### Home Screen Rows (in order)
1. **Continue Watching** (position 0, system row, requires auth)
2. **Networks** (position 1)
3. **Franchises** (position 2)
4. **Directors** (position 3)
5. **Trending Movies** (position 4)
6. **Trending Shows** (position 5)

### Movies Screen Rows
1. **Trending Movies** (position 0, system row)
2. **Popular Movies** (position 1)
3. **Latest 4K Releases** (position 2)

### TV Shows Screen Rows
1. **Trending Shows** (position 0, system row)
2. **Popular Shows** (position 1)

## Common Issues & Troubleshooting

### Issue: "run-as: Package 'com.test1.tv' is not debuggable"
**Solution**: Make sure you're running a **debug** build, not a release build.
```bash
./gradlew installDebug
```

### Issue: "No such table: row_config"
**Solution**: The migration didn't run. Try:
1. Uninstall the app completely
2. Reinstall: `./gradlew installDebug`
3. Check the database version with: `adb shell "run-as com.test1.tv sqlite3 /data/data/com.test1.tv/databases/test1_tv_database 'PRAGMA user_version;'"`

### Issue: row_config table is empty (0 rows)
**Solution**: The initialization didn't run. Check logcat for errors:
```bash
adb logcat | grep -E "(Test1App|RowConfig)"
```

### Issue: Old database version (still version 11)
**Solution**: Database migration failed. To force migration:
1. Clear app data: `adb shell pm clear com.test1.tv`
2. Reinstall: `./gradlew installDebug`

### Issue: Can't see database in Database Inspector
**Solution**:
1. Make sure app is running
2. Click the **reload** button in Database Inspector
3. Try restarting Android Studio
4. Use ADB method as fallback

## Running Unit Tests

Run the RowConfigDao unit tests:
```bash
./gradlew test --tests "com.test1.tv.data.local.dao.RowConfigDaoTest"
```

All 13 tests should pass:
- ✅ Insert and query enabled rows
- ✅ Query all rows regardless of enabled status
- ✅ Get row by ID
- ✅ Update enabled status
- ✅ Update row position
- ✅ Reset to defaults
- ✅ Delete non-system rows only
- ✅ Count rows by screen
- ✅ Verify position ordering
- ✅ Test REPLACE strategy
- ✅ Update all fields

## Verifying No Data Loss

Critical: Make sure existing data wasn't lost during migration.

```bash
adb shell "run-as com.test1.tv sqlite3 /data/data/com.test1.tv/databases/test1_tv_database 'SELECT name as table_name, (SELECT COUNT(*) FROM ' || name || ') as row_count FROM sqlite_master WHERE type=\"table\" AND name NOT LIKE \"sqlite_%\" AND name NOT LIKE \"room_%\";'"
```

Check that:
- `cached_content` still has content (if you had cached data)
- `trakt_account` still has your account (if logged in)
- `continue_watching` still has viewing history
- `watch_status` still has watch progress

## Next Steps

Once all tests pass:
1. ✅ Verify database version is 12
2. ✅ Verify 11 rows exist in row_config
3. ✅ Verify no data loss in other tables
4. ✅ All unit tests pass
5. ✅ App launches without crashes

**You're ready for Phase 2**: Repository Layer implementation!
