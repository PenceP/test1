#!/bin/bash
# Phase 1 Database Testing Queries
# Run these commands to verify database migration and initialization

echo "=========================================="
echo "Phase 1 Database Verification Script"
echo "=========================================="
echo ""

# Get the package name and database path
PACKAGE="com.test1.tv"
DB_PATH="/data/data/$PACKAGE/databases/test1_tv_database"

echo "1. Checking if row_config table exists..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT name FROM sqlite_master WHERE type=\"table\" AND name=\"row_config\";'"
echo ""

echo "2. Checking row_config table schema..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'PRAGMA table_info(row_config);'"
echo ""

echo "3. Checking if index exists..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT name FROM sqlite_master WHERE type=\"index\" AND tbl_name=\"row_config\";'"
echo ""

echo "4. Counting total rows in row_config..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT COUNT(*) as total_rows FROM row_config;'"
echo ""

echo "5. Counting rows by screen type..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT screenType, COUNT(*) as count FROM row_config GROUP BY screenType;'"
echo ""

echo "6. Showing all home screen rows (ordered by position)..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT id, title, rowType, position, enabled FROM row_config WHERE screenType=\"home\" ORDER BY position;'"
echo ""

echo "7. Showing all movies screen rows..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT id, title, rowType, position, enabled FROM row_config WHERE screenType=\"movies\" ORDER BY position;'"
echo ""

echo "8. Showing all tvshows screen rows..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT id, title, rowType, position, enabled FROM row_config WHERE screenType=\"tvshows\" ORDER BY position;'"
echo ""

echo "9. Checking database version..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'PRAGMA user_version;'"
echo ""

echo "10. Verifying no data loss in other tables..."
adb shell "run-as $PACKAGE sqlite3 $DB_PATH 'SELECT name, (SELECT COUNT(*) FROM ' || name || ') as row_count FROM sqlite_master WHERE type=\"table\" AND name NOT LIKE \"sqlite_%\" AND name NOT LIKE \"room_%\";'"
echo ""

echo "=========================================="
echo "Phase 1 Verification Complete"
echo "=========================================="
echo ""
echo "Expected results:"
echo "  - Database version should be 12"
echo "  - Total rows in row_config should be 11"
echo "  - Home screen: 6 rows"
echo "  - Movies screen: 3 rows"
echo "  - TV Shows screen: 2 rows"
echo "  - All rows should have enabled=1"
