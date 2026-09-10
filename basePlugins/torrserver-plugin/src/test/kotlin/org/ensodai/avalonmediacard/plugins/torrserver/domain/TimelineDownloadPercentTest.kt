package org.ensodai.avalonmediacard.plugins.torrserver.domain

import org.ensodai.avalonmediacard.plugins.torrserver.domain.usecase.torrent.TimelineDownloadPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineDownloadPercentTest {

    @Test
    fun `cache plan uses percent of file size ahead of playhead`() {
        val plan = TimelineDownloadPercent.cachePlan(
            timelineBufferPercent = 15,
            fileSizeBytes = 10_000_000_000L, // 10 GB
            durationSeconds = 7200.0,
            positionSeconds = 0.0
        )
        assertEquals(1_500_000_000L, plan.cacheSizeBytes)
        assertEquals(95, plan.readerReadAhead)
        assertEquals(15, plan.preloadCachePercent)
    }

    @Test
    fun `near the end cache shrinks to remaining portion of the file`() {
        val plan = TimelineDownloadPercent.cachePlan(
            timelineBufferPercent = 15,
            fileSizeBytes = 7_200_000_000L,
            durationSeconds = 7200.0,
            positionSeconds = 7100.0
        )
        // remaining ~100/7200 ≈ 1.39%, floor at 5% of file
        assertEquals((7_200_000_000L * 0.05).toLong(), plan.cacheSizeBytes)
    }

    @Test
    fun `small files are not forced above file size by the 64MiB floor`() {
        val plan = TimelineDownloadPercent.cachePlan(
            timelineBufferPercent = 50,
            fileSizeBytes = 20_000_000L,
            durationSeconds = 600.0,
            positionSeconds = 0.0
        )
        // Floor is min(64MiB, fileSize)=fileSize, so the whole small file fits in cache.
        assertEquals(20_000_000L, plan.cacheSizeBytes)
    }

    @Test
    fun `without file size falls back to default cache floor`() {
        val plan = TimelineDownloadPercent.cachePlan(
            timelineBufferPercent = 40,
            fileSizeBytes = null,
            durationSeconds = null,
            positionSeconds = null
        )
        assertEquals(TimelineDownloadPercent.MIN_CACHE_BYTES, plan.cacheSizeBytes)
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
