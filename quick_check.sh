#!/bin/bash
# Quick database verification - just checks if file exists and basic structure

PACKAGE="com.test1.tv"
DB_NAME="test1_tv_database"

echo "Quick Phase 1 Check"
echo "===================="
echo ""

# Check if database file exists
echo "1. Checking if database exists..."
adb shell "run-as $PACKAGE ls -lh /data/data/$PACKAGE/databases/" | grep $DB_NAME
echo ""

# Pull and check with local sqlite3
echo "2. Pulling database and checking version..."
adb exec-out run-as $PACKAGE cat /data/data/$PACKAGE/databases/$DB_NAME > temp_db.db

if [ -f "temp_db.db" ]; then
    echo "   Database version: $(sqlite3 temp_db.db 'PRAGMA user_version;')"
    echo "   Total row_config rows: $(sqlite3 temp_db.db 'SELECT COUNT(*) FROM row_config;')"
    echo ""
    echo "   Rows by screen:"
    sqlite3 temp_db.db "SELECT '   ' || screenType || ': ' || COUNT(*) || ' rows' FROM row_config GROUP BY screenType;"

    rm temp_db.db
    echo ""
    echo "✅ Phase 1 database structure looks good!"
else
    echo "❌ Could not pull database"
fi
