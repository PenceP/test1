#!/bin/bash
# Pull database from device and query locally

PACKAGE="com.test1.tv"
DB_NAME="test1_tv_database"
LOCAL_DB="./temp_database.db"

echo "=========================================="
echo "Phase 1 Database Verification (Local)"
echo "=========================================="
echo ""

# Pull the database from device
echo "Pulling database from device..."
adb exec-out run-as $PACKAGE cat /data/data/$PACKAGE/databases/$DB_NAME > $LOCAL_DB

if [ ! -f "$LOCAL_DB" ]; then
    echo "❌ Failed to pull database"
    exit 1
fi

echo "✅ Database pulled successfully"
echo ""

# Now query it locally
echo "1. Checking if row_config table exists..."
sqlite3 $LOCAL_DB "SELECT name FROM sqlite_master WHERE type='table' AND name='row_config';"
echo ""

echo "2. Checking database version..."
sqlite3 $LOCAL_DB "PRAGMA user_version;"
echo ""

echo "3. Counting total rows in row_config..."
sqlite3 $LOCAL_DB "SELECT COUNT(*) as total_rows FROM row_config;"
echo ""

echo "4. Counting rows by screen type..."
sqlite3 $LOCAL_DB "SELECT screenType, COUNT(*) as count FROM row_config GROUP BY screenType;"
echo ""

echo "5. Showing all rows with details..."
sqlite3 $LOCAL_DB -header -column "SELECT id, screenType, title, rowType, position, enabled, isSystemRow FROM row_config ORDER BY screenType, position;"
echo ""

echo "6. Home screen rows (detailed)..."
sqlite3 $LOCAL_DB -header -column "SELECT title, rowType, position, enabled, requiresAuth, isSystemRow FROM row_config WHERE screenType='home' ORDER BY position;"
echo ""

echo "7. Movies screen rows..."
sqlite3 $LOCAL_DB -header -column "SELECT title, rowType, position, enabled FROM row_config WHERE screenType='movies' ORDER BY position;"
echo ""

echo "8. TV Shows screen rows..."
sqlite3 $LOCAL_DB -header -column "SELECT title, rowType, position, enabled FROM row_config WHERE screenType='tvshows' ORDER BY position;"
echo ""

# Cleanup
rm $LOCAL_DB
echo "✅ Cleaned up temporary database file"
echo ""

echo "=========================================="
echo "Phase 1 Verification Complete"
echo "=========================================="
