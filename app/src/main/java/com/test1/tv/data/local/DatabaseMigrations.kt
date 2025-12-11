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

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // This migration was part of a previous update, keeping for compatibility
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create premiumize_accounts table for debrid service
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS premiumize_accounts (
                providerId TEXT PRIMARY KEY NOT NULL,
                apiKey TEXT NOT NULL,
                customerId TEXT,
                username TEXT,
                email TEXT,
                accountStatus TEXT,
                expiresAt INTEGER,
                pointsUsed REAL,
                pointsAvailable REAL,
                spaceLimitBytes INTEGER,
                spaceUsedBytes INTEGER,
                fairUsageLimitBytes INTEGER,
                fairUsageUsedBytes INTEGER,
                lastVerifiedAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create player_settings table for video player preferences
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS player_settings (
                id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                skipMode TEXT NOT NULL DEFAULT 'instant',
                defaultSubtitleLanguage TEXT DEFAULT 'en',
                defaultAudioLanguage TEXT,
                subtitleDelayMs INTEGER NOT NULL DEFAULT 0,
                audioDelayMs INTEGER NOT NULL DEFAULT 0,
                autoplayNextEpisode INTEGER NOT NULL DEFAULT 1,
                rememberPosition INTEGER NOT NULL DEFAULT 1,
                autoplayCountdownSeconds INTEGER NOT NULL DEFAULT 15
            )
        """)

        // Insert default settings row
        database.execSQL("""
            INSERT OR IGNORE INTO player_settings (id) VALUES (1)
        """)
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add decoder mode, tunneling, and buffer settings to player_settings
        database.execSQL("ALTER TABLE player_settings ADD COLUMN decoderMode TEXT NOT NULL DEFAULT 'auto'")
        database.execSQL("ALTER TABLE player_settings ADD COLUMN tunnelingEnabled INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE player_settings ADD COLUMN minBufferMs INTEGER NOT NULL DEFAULT 30000")
        database.execSQL("ALTER TABLE player_settings ADD COLUMN maxBufferMs INTEGER NOT NULL DEFAULT 60000")
        database.execSQL("ALTER TABLE player_settings ADD COLUMN bufferForPlaybackMs INTEGER NOT NULL DEFAULT 2500")
        database.execSQL("ALTER TABLE player_settings ADD COLUMN bufferForPlaybackAfterRebufferMs INTEGER NOT NULL DEFAULT 5000")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create playback_progress table for local playback tracking
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS playback_progress (
                id TEXT PRIMARY KEY NOT NULL,
                tmdbId INTEGER NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                posterUrl TEXT,
                showTitle TEXT,
                season INTEGER,
                episode INTEGER,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                percent REAL NOT NULL,
                lastWatchedAt INTEGER NOT NULL,
                syncedToTrakt INTEGER NOT NULL DEFAULT 0
            )
        """)

        // Create indices for efficient queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_playback_progress_tmdbId_type
            ON playback_progress (tmdbId, type)
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_playback_progress_lastWatchedAt
            ON playback_progress (lastWatchedAt)
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_playback_progress_syncedToTrakt
            ON playback_progress (syncedToTrakt)
        """)
    }
}
