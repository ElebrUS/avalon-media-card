package org.ensodai.avalonmediacard.plugins.torrserver.domain.usecase.torrent

import kotlin.math.roundToInt

/**
 * Maps a user-facing **timeline** buffer (percent of runtime) onto TorrServer
 * [ReaderReadAHead], which is a percent of the torrent file around the current reader.
 *
 * Resolution order for the requested percent:
 * 1. personal user override (if set)
 * 2. global admin / plugin system setting
 * 3. [DEFAULT_TIMELINE_BUFFER_PERCENT]
 */
object TimelineDownloadPercent {
    const val MIN_READER_READ_AHEAD = 5
    const val MAX_READER_READ_AHEAD = 100
    const val DEFAULT_TIMELINE_BUFFER_PERCENT = 15
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

    /**
     * @param timelineBufferPercent how much of the **full runtime** to keep downloaded
     *   ahead of the playhead (capped by time remaining)
     * @param durationSeconds actual or catalog duration of the playing file
     * @param positionSeconds current / resume position on the timeline
     */
    fun readerReadAhead(
        timelineBufferPercent: Int,
        durationSeconds: Double?,
        positionSeconds: Double?
    ): Int {
        val requested = timelineBufferPercent.coerceIn(0, 100)
        val duration = durationSeconds?.takeIf { it > 0.0 }
            ?: return requested.coerceIn(MIN_READER_READ_AHEAD, MAX_READER_READ_AHEAD)

        val position = (positionSeconds ?: 0.0).coerceIn(0.0, duration)
        val remaining = (duration - position).coerceAtLeast(0.0)
        val desiredAhead = duration * (requested / 100.0)
        val actualAhead = minOf(desiredAhead, remaining)
        val fileWindowPercent = ((actualAhead / duration) * 100.0).roundToInt()
        return fileWindowPercent.coerceIn(MIN_READER_READ_AHEAD, MAX_READER_READ_AHEAD)
    }
}
