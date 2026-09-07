package org.ensodai.avalonmediacard.tmdb

import org.ensodai.avalonmediacard.contract.slot.EpisodeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SeasonFreshnessPolicyTest {

    private val policy = SeasonFreshnessPolicy()
    private val fixedNow = Instant.parse("2026-09-07T12:00:00Z")

    private fun createEpisode(
        number: Int,
        name: String,
        airDate: String? = "2026-09-01",
        stillUrl: String? = "https://image.tmdb.org/still$number.jpg",
        overview: String? = null,
        voteAverage: Double? = null,
        runtime: Int? = null
    ) = EpisodeItem(
        id = "ep-$number",
        episodeNumber = number,
        name = name,
        overview = overview,
        stillUrl = stillUrl,
        airDate = airDate,
        voteAverage = voteAverage,
        runtime = runtime
    )

    @Test
    fun testIsPlaceholderTitle() {
        assertTrue(policy.isPlaceholderTitle("Эпизод 8"))
        assertTrue(policy.isPlaceholderTitle("Эпизод 10"))
        assertTrue(policy.isPlaceholderTitle("Episode 1"))
        assertTrue(policy.isPlaceholderTitle("Серия 5"))
        assertTrue(policy.isPlaceholderTitle(""))
        assertTrue(policy.isPlaceholderTitle("   "))
        assertTrue(policy.isPlaceholderTitle(null))

        assertFalse(policy.isPlaceholderTitle("Серая слизь"))
        assertFalse(policy.isPlaceholderTitle("Прощание"))
        assertFalse(policy.isPlaceholderTitle("Трой"))
        assertFalse(policy.isPlaceholderTitle("Пилотная серия"))
    }

    @Test
    fun testHasPlaceholdersDetection() {
        val todayStr = fixedNow.toString().substringBefore("T")
        val withPlaceholder = listOf(
            createEpisode(1, "Начало", airDate = "2026-08-01"),
            createEpisode(2, "Эпизод 2", airDate = todayStr)
        )
        assertTrue(policy.hasPlaceholders(withPlaceholder, fixedNow))

        val withoutPlaceholder = listOf(
            createEpisode(1, "Начало", airDate = "2026-08-01"),
            createEpisode(2, "Серая слизь", airDate = todayStr)
        )
        assertFalse(policy.hasPlaceholders(withoutPlaceholder, fixedNow))

        // Archive episode with missing still is NOT considered a placeholder
        val archiveMissingStill = listOf(
            createEpisode(1, "Начало", airDate = "2020-01-01", stillUrl = null)
        )
        assertFalse(policy.hasPlaceholders(archiveMissingStill, fixedNow))

        // Recent episode with missing still IS considered a placeholder
        val recentMissingStill = listOf(
            createEpisode(1, "Начало", airDate = todayStr, stillUrl = null)
        )
        assertTrue(policy.hasPlaceholders(recentMissingStill, fixedNow))
    }

    @Test
    fun testHotWindowDetection() {
        val todayStr = fixedNow.toString().substringBefore("T")
        val episodesToday = listOf(createEpisode(1, "Новый день", airDate = todayStr))
        assertTrue(policy.isInHotWindow(episodesToday, fixedNow))

        val yesterdayStr = (fixedNow - 1.days).toString().substringBefore("T")
        val episodesYesterday = listOf(createEpisode(1, "Вчера", airDate = yesterdayStr))
        assertTrue(policy.isInHotWindow(episodesYesterday, fixedNow))

        val episodesFarPast = listOf(createEpisode(1, "Давным давно", airDate = "2026-01-01"))
        assertFalse(policy.isInHotWindow(episodesFarPast, fixedNow))
    }

    @Test
    fun testActiveOngoingDetection() {
        val futureDate = (fixedNow + 7.days).toString().substringBefore("T")
        val episodesFuture = listOf(createEpisode(1, "Будущая серия", airDate = futureDate))
        assertTrue(policy.isActiveOngoing(episodesFuture, fixedNow, expectedCount = null))

        // Incomplete season: expected 10 episodes, but have 8
        val episodesIncomplete = listOf(createEpisode(1, "Серия 1", airDate = "2025-01-01"))
        assertTrue(policy.isActiveOngoing(episodesIncomplete, fixedNow, expectedCount = 10))

        // Fully completed archive season
        val episodesArchive = listOf(createEpisode(1, "Серия 1", airDate = "2025-01-01"))
        assertFalse(policy.isActiveOngoing(episodesArchive, fixedNow, expectedCount = 1))
    }

    @Test
    fun testEvaluateFreshnessStatuses() {
        // 1. Empty cache -> EXPIRED
        assertEquals(FreshnessStatus.EXPIRED, policy.evaluate(null, null, now = fixedNow))
        assertEquals(FreshnessStatus.EXPIRED, policy.evaluate(emptyList(), null, now = fixedNow))

        // 2. Season with placeholder title
        val placeholderEpisodes = listOf(
            createEpisode(1, "Эпизод 8", airDate = fixedNow.toString().substringBefore("T"))
        )
        // Never updated or updated > 15m ago -> STALE_REVALIDATE
        assertEquals(
            FreshnessStatus.STALE_REVALIDATE,
            policy.evaluate(placeholderEpisodes, lastUpdatedAt = null, now = fixedNow)
        )
        assertEquals(
            FreshnessStatus.STALE_REVALIDATE,
            policy.evaluate(placeholderEpisodes, lastUpdatedAt = fixedNow - 20.minutes, now = fixedNow)
        )
        // Updated 5 minutes ago -> FRESH
        assertEquals(
            FreshnessStatus.FRESH,
            policy.evaluate(placeholderEpisodes, lastUpdatedAt = fixedNow - 5.minutes, now = fixedNow)
        )

        // 3. Hot Window season (no placeholders)
        val hotWindowEpisodes = listOf(
            createEpisode(1, "Реальный заголовок", airDate = fixedNow.toString().substringBefore("T"))
        )
        assertEquals(
            FreshnessStatus.FRESH,
            policy.evaluate(hotWindowEpisodes, lastUpdatedAt = fixedNow - 10.minutes, now = fixedNow)
        )
        assertEquals(
            FreshnessStatus.STALE_REVALIDATE,
            policy.evaluate(hotWindowEpisodes, lastUpdatedAt = fixedNow - 35.minutes, now = fixedNow)
        )

        // 4. Archive season (>30 days ago, full count)
        val archiveEpisodes = listOf(
            createEpisode(1, "Архивная классика", airDate = "2020-01-01")
        )
        assertEquals(
            FreshnessStatus.FRESH,
            policy.evaluate(archiveEpisodes, lastUpdatedAt = fixedNow - 10.days, expectedEpisodeCount = 1, now = fixedNow)
        )
        assertEquals(
            FreshnessStatus.STALE_REVALIDATE,
            policy.evaluate(archiveEpisodes, lastUpdatedAt = fixedNow - 35.days, expectedEpisodeCount = 1, now = fixedNow)
        )
    }
}
