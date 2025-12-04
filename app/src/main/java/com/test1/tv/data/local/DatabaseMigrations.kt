package com.test1.tv.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create row_config table
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

        // Create index for efficient queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_row_config_screenType_position
            ON row_config (screenType, position)
        """)
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create sync_metadata table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                `key` TEXT PRIMARY KEY NOT NULL,
                lastSyncedAt INTEGER NOT NULL,
                traktActivityTimestamp TEXT
            )
        """)
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create media_enrichment table for TMDB enrichment caching
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS media_enrichment (
                tmdbId INTEGER PRIMARY KEY NOT NULL,
                posterUrl TEXT,
                backdropUrl TEXT,
                logoUrl TEXT,
                genres TEXT,
                cast TEXT,
                runtime TEXT,
                certification TEXT,
                lastUpdated INTEGER NOT NULL
            )
        """)
    }
}
