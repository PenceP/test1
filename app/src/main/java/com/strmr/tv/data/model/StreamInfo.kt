package com.strmr.tv.data.model

import com.strmr.tv.data.repository.Quality

/**
 * Parsed stream/link information from Torrentio or other scrapers
 */
data class StreamInfo(
    val infoHash: String,
    val fileName: String,
    val quality: Quality,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val seeds: Int?,
    val source: String,            // RARBG, 1337x, YTS, etc.
    val codec: String?,            // x265, x264, HEVC
    val audio: String?,            // DTS, AAC, Atmos
    val hdr: String?,              // HDR, HDR10, DV
    val fileIdx: Int?,             // File index in torrent
    val isCached: Boolean = false,
    val debridProvider: DebridProvider? = null,
    val estimatedBitrateMbps: Double? = null,
    val isFilteredOut: Boolean = false,
    val filterReason: String? = null
) {
    /**
     * Format the display name with badges
     * Format: [PM+] [4K] Filename [11.2GB]
     */
    fun getDisplayName(): String {
        return fileName
    }

    /**
     * Get the debrid badge text
     * PM+ = cached on Premiumize, PM = not cached
     * RD+ = cached on RealDebrid, RD = not cached
     */
    fun getDebridBadge(): String? {
        return when (debridProvider) {
            DebridProvider.PREMIUMIZE -> if (isCached) "PM+" else "PM"
            DebridProvider.REAL_DEBRID -> if (isCached) "RD+" else "RD"
            DebridProvider.ALL_DEBRID -> if (isCached) "AD+" else "AD"
            null -> null
        }
    }

    /**
     * Get quality badge text
     */
    fun getQualityBadge(): String {
        return quality.label
    }

    companion object {
        /**
         * Parse a Torrentio stream title into StreamInfo
         * Example title: "Guardians.of" +
         *   ".the.Galaxy.Vol.2.2017.2160p.UHD.BluRay.REMUX.HDR.HEVC.Atmos-EPSiLON\n" +
         *   "👤 150 💾 67.00 GB ⚙️ RARBG"
         */
        fun parseFromTorrentio(
            infoHash: String,
            title: String,
            fileIdx: Int? = null
        ): StreamInfo {
            val lines = title.split("\n")
            val fileName = lines.firstOrNull()?.trim() ?: title

            // Parse metadata line (seeds, size, source)
            val metaLine = lines.getOrNull(1) ?: ""

            // Extract seeds (👤 150)
            val seedsMatch = Regex("""👤\s*(\d+)""").find(metaLine)
            val seeds = seedsMatch?.groupValues?.get(1)?.toIntOrNull()

            // Extract size (💾 67.00 GB)
            val sizeMatch = Regex("""💾\s*([\d.]+)\s*(GB|MB|TB)""", RegexOption.IGNORE_CASE).find(metaLine)
            val sizeValue = sizeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val sizeUnit = sizeMatch?.groupValues?.get(2)?.uppercase() ?: "GB"
            val sizeBytes = when (sizeUnit) {
                "TB" -> (sizeValue * 1024 * 1024 * 1024 * 1024).toLong()
                "GB" -> (sizeValue * 1024 * 1024 * 1024).toLong()
                "MB" -> (sizeValue * 1024 * 1024).toLong()
                else -> (sizeValue * 1024 * 1024 * 1024).toLong()
            }
            val sizeFormatted = "${sizeMatch?.groupValues?.get(1) ?: "?"} ${sizeUnit}"

            // Extract source (⚙️ RARBG)
            val sourceMatch = Regex("""⚙️\s*(\w+)""").find(metaLine)
            val source = sourceMatch?.groupValues?.get(1) ?: "Unknown"

            // Parse quality from filename
            val quality = Quality.fromString(fileName)

            // Parse codec
            val codec = when {
                fileName.contains("x265", ignoreCase = true) ||
                    fileName.contains("HEVC", ignoreCase = true) -> "HEVC"
                fileName.contains("x264", ignoreCase = true) ||
                    fileName.contains("AVC", ignoreCase = true) -> "x264"
                fileName.contains("AV1", ignoreCase = true) -> "AV1"
                else -> null
            }

            // Parse audio
            val audio = when {
                fileName.contains("Atmos", ignoreCase = true) -> "Atmos"
                fileName.contains("TrueHD", ignoreCase = true) -> "TrueHD"
                fileName.contains("DTS-HD", ignoreCase = true) -> "DTS-HD"
                fileName.contains("DTS", ignoreCase = true) -> "DTS"
                fileName.contains("DD+", ignoreCase = true) ||
                    fileName.contains("EAC3", ignoreCase = true) -> "DD+"
                fileName.contains("AAC", ignoreCase = true) -> "AAC"
                else -> null
            }

            // Parse HDR
            val hdr = when {
                fileName.contains("DV", ignoreCase = false) ||
                    fileName.contains("DoVi", ignoreCase = true) ||
                    fileName.contains("Dolby Vision", ignoreCase = true) -> "DV"
                fileName.contains("HDR10+", ignoreCase = true) -> "HDR10+"
                fileName.contains("HDR10", ignoreCase = true) -> "HDR10"
                fileName.contains("HDR", ignoreCase = true) -> "HDR"
                else -> null
            }

            return StreamInfo(
                infoHash = infoHash,
                fileName = fileName,
                quality = quality,
                sizeBytes = sizeBytes,
                sizeFormatted = sizeFormatted,
                seeds = seeds,
                source = source,
                codec = codec,
                audio = audio,
                hdr = hdr,
                fileIdx = fileIdx
            )
        }
    }
}

enum class DebridProvider(val displayName: String) {
    PREMIUMIZE("Premiumize"),
    REAL_DEBRID("Real-Debrid"),
    ALL_DEBRID("AllDebrid")
}
