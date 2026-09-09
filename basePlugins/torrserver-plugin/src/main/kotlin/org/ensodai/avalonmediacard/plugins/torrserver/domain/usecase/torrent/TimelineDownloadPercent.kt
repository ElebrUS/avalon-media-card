package org.ensodai.avalonmediacard.plugins.torrserver.domain.usecase.torrent

import kotlin.math.roundToInt

/**
 * Maps a user-facing **timeline** buffer (percent of runtime) onto TorrServer
 * [ReaderReadAHead], which is a percent of the torrent file around the current reader.
 *
 * File size is a poor proxy: VBR means bytes ≠ playback time. Duration + position
 * from the timeline is the right source. The result is still a file-window percent
 * because that is the only knob TorrServer exposes, but the conversion uses time.
 */
object TimelineDownloadPercent {
    const val MIN_READER_READ_AHEAD = 5
    const val MAX_READER_READ_AHEAD = 100
    const val DEFAULT_TIMELINE_BUFFER_PERCENT = 15
    /** Key under plugin settings (`plugin:torrserver-plugin:…`) and admin system settings. */
    const val SETTING_KEY = "torrserver_timeline_buffer_percent"

    fun parseSetting(raw: String?): Int {
        val digits = raw?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()
        return (digits ?: DEFAULT_TIMELINE_BUFFER_PERCENT).coerceIn(0, 100)
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
