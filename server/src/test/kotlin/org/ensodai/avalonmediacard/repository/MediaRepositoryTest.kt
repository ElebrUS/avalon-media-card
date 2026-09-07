package org.ensodai.avalonmediacard.repository

import kotlinx.coroutines.runBlocking
import org.ensodai.avalonmediacard.contract.classification.AnimeSubType
import org.ensodai.avalonmediacard.contract.model.GenreMetadata
import org.ensodai.avalonmediacard.contract.model.KeywordMetadata
import org.ensodai.avalonmediacard.contract.model.MediaMetadata
import org.ensodai.avalonmediacard.contract.model.ProductionCompanyMetadata
import org.ensodai.avalonmediacard.contract.plugins.GenreDictionaryProvider
import org.ensodai.avalonmediacard.contract.slot.EpisodeItem
import org.ensodai.avalonmediacard.database.AllDatabaseTables
import org.ensodai.avalonmediacard.database.MediaEpisodeTable
import org.ensodai.avalonmediacard.database.UserEpisodeTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MediaRepositoryTest {

    private val fakeGenreDictionaryProvider = object : GenreDictionaryProvider {
        override suspend fun getLocalizedGenres(language: String): Map<String, String> = emptyMap()
    }

    private val repository = MediaRepository(fakeGenreDictionaryProvider)
    private val dbFileName = "test_media_repository_avalon.db"

    @BeforeTest
    fun setup() {
        try {
            java.io.File(dbFileName).delete()
        } catch (_: Exception) {}

        Database.connect("jdbc:sqlite:$dbFileName", driver = "org.sqlite.JDBC")
        transaction {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*AllDatabaseTables)
            for (stmt in statements) {
                exec(stmt)
            }
        }
    }

    @AfterTest
    fun tearDown() {
        try {
            java.io.File(dbFileName).delete()
        } catch (_: Exception) {}
    }

    @Test
    fun testUpsertAndGetAnimeMetadata() = runBlocking {
        val animeMeta = MediaMetadata(
            title = "Клинок, рассекающий демонов",
            originalTitle = "Kimetsu no Yaiba",
            imdbId = "tt9335498",
            description = "Танджиро Камадо становится истребителем демонов...",
            rating = "8.7",
            releaseDate = "2019-04-06",
            status = "Returning Series",
            genres = listOf(GenreMetadata(16, "Анимация"), GenreMetadata(10759, "Боевик и Приключения")),
            keywords = listOf(KeywordMetadata(1, "anime"), KeywordMetadata(2, "demons")),
            productionCompanies = listOf(ProductionCompanyMetadata(1, "ufotable")),
            animeSubType = AnimeSubType.JAPANESE_ANIME
        )

        repository.upsertMetadata(
            catalogId = "tmdb",
            externalId = "85937",
            mediaType = "tv",
            metadata = animeMeta,
            language = "ru"
        )

        val retrieved = repository.getMetadata(catalogId = "tmdb", externalId = "85937", language = "ru")
        assertNotNull(retrieved)
        assertEquals("Клинок, рассекающий демонов", retrieved.title)
        assertEquals("Kimetsu no Yaiba", retrieved.originalTitle)
        assertEquals("tt9335498", retrieved.imdbId)
        assertEquals(AnimeSubType.JAPANESE_ANIME, retrieved.animeSubType)
        assertTrue(retrieved.isAnime)
    }

    @Test
    fun testUpsertAndGetNonAnimeMovieMetadata() = runBlocking {
        val movieMeta = MediaMetadata(
            title = "Семь самураев",
            originalTitle = "七人の侍",
            imdbId = "tt0047478",
            description = "Шедевр Акиры Куросавы...",
            rating = "8.6",
            releaseDate = "1954-04-26",
            status = "Released",
            genres = listOf(GenreMetadata(28, "Боевик"), GenreMetadata(18, "Драма")),
            productionCompanies = listOf(ProductionCompanyMetadata(1, "Toho")),
            animeSubType = AnimeSubType.NOT_ANIME
        )

        repository.upsertMetadata(
            catalogId = "tmdb",
            externalId = "346",
            mediaType = "movie",
            metadata = movieMeta,
            language = "ru"
        )

        val retrieved = repository.getMetadata(catalogId = "tmdb", externalId = "346", language = "ru")
        assertNotNull(retrieved)
        assertEquals("Семь самураев", retrieved.title)
        assertEquals(AnimeSubType.NOT_ANIME, retrieved.animeSubType)
        assertFalse(retrieved.isAnime)
    }

    @Test
    fun testGetMetadataBatchWithDifferentAnimeSubTypes() = runBlocking {
        val donghuaMeta = MediaMetadata(
            title = "Аватар короля",
            originalTitle = "全职高手",
            imdbId = "tt7046092",
            description = "История Е Сю...",
            genres = listOf(GenreMetadata(16, "Анимация")),
            animeSubType = AnimeSubType.CHINESE_DONGHUA
        )

        val westernAnimeMeta = MediaMetadata(
            title = "Аркейн",
            originalTitle = "Arcane",
            imdbId = "tt11126994",
            description = "История сестёр Джинкс и Вай...",
            genres = listOf(GenreMetadata(16, "Анимация")),
            animeSubType = AnimeSubType.ANIME_INSPIRED
        )

        repository.upsertMetadata("tmdb", "1001", "tv", donghuaMeta, "ru")
        repository.upsertMetadata("tmdb", "1002", "tv", westernAnimeMeta, "ru")

        val batch = repository.getMetadataBatch("tmdb", listOf("1001", "1002"), "ru")
        assertEquals(2, batch.size)

        val donghua = batch["1001"]
        assertNotNull(donghua)
        assertEquals(AnimeSubType.CHINESE_DONGHUA, donghua.animeSubType)
        assertTrue(donghua.isAnime)

        val western = batch["1002"]
        assertNotNull(western)
        assertEquals(AnimeSubType.ANIME_INSPIRED, western.animeSubType)
        assertTrue(western.isAnime)
    }

    @Test
    fun testUpdateExistingMediaMetadataPreservesOrUpdatesAnimeSubType() = runBlocking {
        val initialMeta = MediaMetadata(
            title = "Initial Title",
            animeSubType = AnimeSubType.NOT_ANIME
        )
        repository.upsertMetadata("tmdb", "2001", "movie", initialMeta, "ru")

        val before = repository.getMetadata("tmdb", "2001", "ru")
        assertNotNull(before)
        assertEquals(AnimeSubType.NOT_ANIME, before.animeSubType)
        assertFalse(before.isAnime)

        val updatedMeta = MediaMetadata(
            title = "Updated Title (Reclassified)",
            animeSubType = AnimeSubType.JAPANESE_ANIME
        )
        repository.upsertMetadata("tmdb", "2001", "movie", updatedMeta, "ru")

        val after = repository.getMetadata("tmdb", "2001", "ru")
        assertNotNull(after)
        assertEquals("Updated Title (Reclassified)", after.title)
        assertEquals(AnimeSubType.JAPANESE_ANIME, after.animeSubType)
        assertTrue(after.isAnime)
    }

    @Test
    fun testAutoHealLegacyCachedAnimeRecords() = runBlocking {
        // Симулируем запись, созданную до миграции V50, где animeSubType остался по умолчанию NOT_ANIME,
        // но в БД сохранены жанр Animation (16) и японское оригинальное название.
        val legacyMeta = MediaMetadata(
            title = "Конец Евангелиона",
            originalTitle = "新世紀エヴァンゲリオン劇場版 Air／まごころを、君に",
            genres = listOf(GenreMetadata(16, "Анимация"), GenreMetadata(878, "Фантастика")),
            animeSubType = AnimeSubType.NOT_ANIME
        )

        repository.upsertMetadata("tmdb", "18491", "movie", legacyMeta, "ru")

        // При чтении из кэша MediaRepository должен автоматически переклассифицировать запись и обновить БД
        val healed = repository.getMetadata("tmdb", "18491", "ru")
        assertNotNull(healed)
        assertEquals(AnimeSubType.JAPANESE_ANIME, healed.animeSubType)
        assertTrue(healed.isAnime)
    }

    @Test
    fun testSafeUpsertSeasonDetailsPreservesUserEpisodeProgressAndUpdatesTitles() = runBlocking {
        // 1. Создаем метаданные сериала (Silo)
        repository.upsertMetadata(
            catalogId = "tmdb",
            externalId = "94997",
            mediaType = "tv",
            metadata = MediaMetadata(title = "Укрытие (Silo)"),
            language = "ru"
        )

        // 2. Первоначальный кэш с заглушкой TMDB "Эпизод 2"
        val initialEpisodes = listOf(
            EpisodeItem(
                id = "1",
                episodeNumber = 1,
                name = "Инженер",
                airDate = "2026-09-01",
                overview = null,
                stillUrl = null,
                voteAverage = null,
                runtime = null
            ),
            EpisodeItem(
                id = "2",
                episodeNumber = 2,
                name = "Эпизод 2",
                airDate = "2026-09-08",
                overview = null,
                stillUrl = null,
                voteAverage = null,
                runtime = null
            )
        )
        repository.upsertSeasonDetails("tmdb", "94997", "ru", 1, initialEpisodes)

        val cachedInitial = repository.getSeasonDetails("tmdb", "94997", "ru", 1)
        assertNotNull(cachedInitial)
        assertEquals(2, cachedInitial.episodes.size)
        assertEquals("Эпизод 2", cachedInitial.episodes[1].name)
        val initialEpisode2Id = cachedInitial.episodes[1].id

        // 3. Пользователь посмотрел 1200 секунд второй серии и отметил ее просмотренной
        val testUserId = Uuid.random()
        val parsedEpId = Uuid.parse(initialEpisode2Id)
        transaction {
            UserEpisodeTable.insert {
                it[this.userId] = testUserId
                it[this.episodeId] = parsedEpId
                it[this.progressSeconds] = 1200
                it[this.durationSeconds] = 3000
                it[this.isWatched] = true
                it[this.lastWatchedAt] = Clock.System.now()
            }
        }

        // 4. Повторный upsert с актуальным переводом с TMDB ("Серая слизь")
        val updatedEpisodes = listOf(
            EpisodeItem(
                id = "1",
                episodeNumber = 1,
                name = "Инженер",
                airDate = "2026-09-01",
                overview = null,
                stillUrl = null,
                voteAverage = null,
                runtime = null
            ),
            EpisodeItem(
                id = "2",
                episodeNumber = 2,
                name = "Серая слизь",
                airDate = "2026-09-08",
                overview = null,
                stillUrl = null,
                voteAverage = null,
                runtime = null
            )
        )
        repository.upsertSeasonDetails("tmdb", "94997", "ru", 1, updatedEpisodes)

        // 5. Проверяем, что название обновилось, ID серии НЕ изменился, а прогресс пользователя сохранился!
        val cachedUpdated = repository.getSeasonDetails("tmdb", "94997", "ru", 1)
        assertNotNull(cachedUpdated)
        assertEquals(2, cachedUpdated.episodes.size)
        assertEquals("Серая слизь", cachedUpdated.episodes[1].name)
        assertEquals(initialEpisode2Id, cachedUpdated.episodes[1].id, "ID серии обязан сохраниться (in-place upsert)")

        // Проверяем запись в таблице user_episodes
        val userProgressRow = transaction {
            UserEpisodeTable.selectAll().where {
                UserEpisodeTable.userId eq testUserId
            }.firstOrNull()
        }
        assertNotNull(userProgressRow, "Запись прогресса пользователя НЕ должна быть удалена каскадом!")
        assertEquals(1200L, userProgressRow[UserEpisodeTable.progressSeconds])
        assertTrue(userProgressRow[UserEpisodeTable.isWatched])
    }
}
