package org.ensodai.avalonmediacard.plugins.torrserver.domain.usecase.torrent

/**
 * Maps a user-facing **timeline** buffer (percent of runtime / file) onto TorrServer
 * cache limits.
 *
 * Important TorrServer semantics:
 * - [ReaderReadAHead] is a percent of **CacheSize** ahead vs behind the playhead — not of the file.
 * - Download volume is bounded by **CacheSize** (pieces outside the reader window are dropped).
 *
 * So “keep ~N% of the title ahead” ≈ set CacheSize ≈ N% of the file (capped by remaining
 * runtime), and keep ReaderReadAHead high so most of that cache sits ahead of the playhead.
 */
object TimelineDownloadPercent {
    const val MIN_READER_READ_AHEAD = 5
    const val MAX_READER_READ_AHEAD = 100
    /** Prefer almost all of the cache ahead of the playhead. */
    const val FORWARD_READER_READ_AHEAD = 95
    const val DEFAULT_TIMELINE_BUFFER_PERCENT = 15
    const val MIN_CACHE_BYTES = 64L * 1024 * 1024 // TorrServer default floor
    /** Key under user integration settings and plugin/admin system settings. */
    const val SETTING_KEY = "torrserver_timeline_buffer_percent"

    /** Parses a required/global value; blank falls back to the default. */
    fun parseSetting(raw: String?): Int {
        return parseOptional(raw) ?: DEFAULT_TIMELINE_BUFFER_PERCENT
    }

    /** Parses an optional personal override; blank/null means “use global”. */
    fun parseOptional(raw: String?): Int? {
        val digits = raw?.trim()?.removeSuffix("%")?.trim().orEmpty()
        if (digits.isEmpty()) return null
        return digits.toIntOrNull()?.coerceIn(0, 100)
    }

    fun resolve(userOverrideRaw: String?, globalRaw: String?, globalFromManager: Int?): Int {
        parseOptional(userOverrideRaw)?.let { return it }
        if (globalFromManager != null) return globalFromManager.coerceIn(0, 100)
        return parseSetting(globalRaw)
    }

    data class TorrServerCachePlan(
        val cacheSizeBytes: Long,
        val readerReadAhead: Int,
        val preloadCachePercent: Int
    )

    /**
     * @param timelineBufferPercent share of the title to keep buffered ahead of the playhead
     * @param fileSizeBytes size of the playing torrent file (preferred)
     * @param durationSeconds catalog/probe duration when available
     * @param positionSeconds resume / playhead position
     */
    fun cachePlan(
        timelineBufferPercent: Int,
        fileSizeBytes: Long?,
        durationSeconds: Double?,
        positionSeconds: Double?
    ): TorrServerCachePlan {
        val requested = timelineBufferPercent.coerceIn(0, 100)
        val size = fileSizeBytes?.takeIf { it > 0 }

        val remainingRatio = if (durationSeconds != null && durationSeconds > 0.0) {
            val position = (positionSeconds ?: 0.0).coerceIn(0.0, durationSeconds)
            ((durationSeconds - position) / durationSeconds).coerceIn(0.0, 1.0)
        } else {
            1.0
        }

        val desiredRatio = minOf(requested / 100.0, remainingRatio.coerceAtLeast(0.05))
        val cacheSize = if (size != null) {
            val desired = (size.toDouble() * desiredRatio).toLong().coerceAtLeast(1L)
            val floor = minOf(MIN_CACHE_BYTES, size)
            desired.coerceIn(floor, size)
        } else {
            // Without file size, keep a modest RAM cache; TorrServer cannot honour a file-% window.
            MIN_CACHE_BYTES
        }

        val preload = requested.coerceIn(0, 100)
        return TorrServerCachePlan(
            cacheSizeBytes = cacheSize,
            readerReadAhead = FORWARD_READER_READ_AHEAD.coerceIn(MIN_READER_READ_AHEAD, MAX_READER_READ_AHEAD),
            preloadCachePercent = preload
        )
    }

    @Deprecated("ReaderReadAHead is a cache-share, not a file-share; use cachePlan()")
    fun readerReadAhead(
        timelineBufferPercent: Int,
        durationSeconds: Double?,
        positionSeconds: Double?
    ): Int {
        return cachePlan(timelineBufferPercent, null, durationSeconds, positionSeconds).readerReadAhead
    }
}
