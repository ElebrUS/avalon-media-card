package org.ensodai.avalonmediacard.plugins.torrserver.domain

import org.ensodai.avalonmediacard.plugins.torrserver.domain.usecase.torrent.TimelineDownloadPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineDownloadPercentTest {

    @Test
    fun `at start requested percent maps one-to-one onto the file window`() {
        val result = TimelineDownloadPercent.readerReadAhead(
            timelineBufferPercent = 15,
            durationSeconds = 7200.0,
            positionSeconds = 0.0
        )
        assertEquals(15, result)
    }

    @Test
    fun `mid-playback keeps the same time window when remaining is larger`() {
        val result = TimelineDownloadPercent.readerReadAhead(
            timelineBufferPercent = 10,
            durationSeconds = 7200.0,
            positionSeconds = 3600.0
        )
        assertEquals(10, result)
    }

    @Test
    fun `near the end the window shrinks to remaining time and respects TorrServer minimum`() {
        val result = TimelineDownloadPercent.readerReadAhead(
            timelineBufferPercent = 15,
            durationSeconds = 7200.0,
            positionSeconds = 7100.0
        )
        assertEquals(5, result)
    }

    @Test
    fun `without duration falls back to the requested percent`() {
        val result = TimelineDownloadPercent.readerReadAhead(
            timelineBufferPercent = 40,
            durationSeconds = null,
            positionSeconds = 120.0
        )
        assertEquals(40, result)
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(5, TimelineDownloadPercent.readerReadAhead(0, null, null))
        assertEquals(100, TimelineDownloadPercent.readerReadAhead(250, 100.0, 0.0))
    }

    @Test
    fun `parseOptional treats blank as unset`() {
        assertNull(TimelineDownloadPercent.parseOptional(null))
        assertNull(TimelineDownloadPercent.parseOptional("  "))
        assertEquals(20, TimelineDownloadPercent.parseOptional("20%"))
    }

    @Test
    fun `resolve prefers personal override then global manager then plugin setting`() {
        assertEquals(30, TimelineDownloadPercent.resolve("30", "10", 15))
        assertEquals(15, TimelineDownloadPercent.resolve("", "10", 15))
        assertEquals(10, TimelineDownloadPercent.resolve(null, "10", null))
        assertEquals(15, TimelineDownloadPercent.resolve(null, null, null))
    }
}
