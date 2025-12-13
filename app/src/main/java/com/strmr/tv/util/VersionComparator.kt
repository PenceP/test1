package com.strmr.tv.util

/**
 * Compares semantic versions with optional pre-release suffixes.
 *
 * Version format: MAJOR.MINOR.PATCH[-PRERELEASE]
 *
 * Pre-release precedence (lowest to highest):
 *   alpha < beta < (stable/no suffix)
 *
 * Examples:
 *   1.0.0-alpha < 1.0.0-beta < 1.0.0 < 1.0.1-alpha < 1.0.1
 */
object VersionComparator {

    private val VERSION_REGEX = """(\d+)\.(\d+)\.(\d+)(?:-(\w+))?""".toRegex()

    data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: String? // null = stable release
    ) : Comparable<ParsedVersion> {

        private val prereleaseOrder: Int
            get() = when (prerelease?.lowercase()) {
                "alpha" -> 0
                "beta" -> 1
                null -> 2  // Stable release has highest precedence
                else -> 2  // Unknown pre-release treated as stable
            }

        override fun compareTo(other: ParsedVersion): Int {
            // Compare major.minor.patch first
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            // Same version number - compare pre-release
            return prereleaseOrder.compareTo(other.prereleaseOrder)
        }

        override fun toString(): String {
            return if (prerelease != null) {
                "$major.$minor.$patch-$prerelease"
            } else {
                "$major.$minor.$patch"
            }
        }
    }

    /**
     * Parse a version string into components.
     * @param version Version string (e.g., "1.2.3-beta")
     * @return ParsedVersion or null if invalid format
     */
    fun parse(version: String): ParsedVersion? {
        val match = VERSION_REGEX.matchEntire(version.trim()) ?: return null

        val (major, minor, patch, prerelease) = match.destructured

        return ParsedVersion(
            major = major.toIntOrNull() ?: return null,
            minor = minor.toIntOrNull() ?: return null,
            patch = patch.toIntOrNull() ?: return null,
            prerelease = prerelease.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Compare two version strings.
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    fun compare(v1: String, v2: String): Int {
        val parsed1 = parse(v1) ?: throw IllegalArgumentException("Invalid version: $v1")
        val parsed2 = parse(v2) ?: throw IllegalArgumentException("Invalid version: $v2")
        return parsed1.compareTo(parsed2)
    }

    /**
     * Check if remote version is newer than local version.
     */
    fun isNewerVersion(localVersion: String, remoteVersion: String): Boolean {
        return try {
            compare(remoteVersion, localVersion) > 0
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
