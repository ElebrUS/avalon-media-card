package org.ensodai.avalonmediacard.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ensodai.avalonmediacard.contract.model.EntityType
import org.ensodai.avalonmediacard.contract.model.MediaCatalog
import org.ensodai.avalonmediacard.contract.model.MediaKey
import org.ensodai.avalonmediacard.contract.model.MediaProvider
import org.ensodai.avalonmediacard.contract.model.MediaStatus
import org.ensodai.avalonmediacard.contract.model.MediaType
import org.ensodai.avalonmediacard.repository.UserEpisodeNotificationRepository
import org.ensodai.avalonmediacard.repository.UserEpisodeRepository
import org.ensodai.avalonmediacard.repository.UserMovieRepository
import org.ensodai.avalonmediacard.repository.UserRepository
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Single
class EpisodeNotificationSyncWorker(
    private val userMovieRepository: UserMovieRepository,
    private val userEpisodeRepository: UserEpisodeRepository,
    private val userRepository: UserRepository,
    private val catalog: MediaCatalog,
    private val notificationRepository: UserEpisodeNotificationRepository
) {
    private val logger = LoggerFactory.getLogger(EpisodeNotificationSyncWorker::class.java)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("EpisodeNotificationSyncWorkerScope"))

    fun start() {
        if (job != null) return
        logger.info("Starting EpisodeNotificationSyncWorker loop...")
        job = scope.launch {
            delay(10_000.milliseconds)
            while (isActive) {
                try {
                    syncAllUsers()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error in EpisodeNotificationSyncWorker loop", e)
                }
                delay(3.hours)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun syncUser(userId: Uuid) {
        try {
            val userMovies = userMovieRepository.getUserMovies(userId)
            val allShows = userMovies.filter {
                (it.status == MediaStatus.WATCHING || it.status == MediaStatus.PLANNED) && it.mediaType == MediaType.TV
            }
            val plannedMovies = userMovies.filter {
                it.status == MediaStatus.PLANNED && it.mediaType == MediaType.MOVIE
            }

            if (allShows.isEmpty() && plannedMovies.isEmpty()) return

            val now = Clock.System.now()
            val todayStr = now.toString().substringBefore("T")
            val thirtyDaysAgo = now - 30.days
            val thirtyDaysAgoStr = thirtyDaysAgo.toString().substringBefore("T")

            // 1. Обработка сериалов (WATCHING и PLANNED)
            for (movie in allShows) {
                val mediaKey = MediaKey(MediaProvider.Tmdb, EntityType.TV, movie.mediaId)
                val catalogId = movie.catalogId.ifEmpty { "tmdb" }
                val internalMediaId = notificationRepository.getOrCreateMediaId(catalogId, movie.mediaId, "tv")

                val mediaDetails = runCatching {
                    catalog.getMediaDetails(mediaKey, requireSeasons = true, requireVideos = false, language = "ru")
                }.getOrNull() ?: continue

                val userProgressList = userEpisodeRepository.getEpisodesProgress(userId, movie.mediaId, catalogId)
                val watchedMap = userProgressList.associate { "${it.season}_${it.episode}" to it.isWatched }

                // Для сериалов в статусе "В планах" (PLANNED) не спамим старыми сериями:
                // Уведомляем только о сериях, выходящих не раньше момента добавления сериала в план
                val addedAt = if (movie.createdAt != Instant.DISTANT_PAST) movie.createdAt else now
                val cutoffDateStr = (addedAt - 3.days).toString().substringBefore("T")
                val minDateStr = if (movie.status == MediaStatus.PLANNED) {
                    maxOf(thirtyDaysAgoStr, cutoffDateStr)
                } else {
                    thirtyDaysAgoStr
                }

                val seasons = mediaDetails.seasons
                val latestSeasons = seasons
                    .filter { it.seasonNumber > 0 }
                    .sortedByDescending { it.seasonNumber }
                    .take(2)

                for (seasonSummary in latestSeasons) {
                    val seasonNumber = seasonSummary.seasonNumber
                    val episodes = runCatching {
                        catalog.getSeasonDetails(mediaKey, seasonNumber, language = "ru")
                    }.getOrNull() ?: emptyList()

                    for (ep in episodes) {
                        val airDate = ep.airDate ?: continue
                        if (airDate < minDateStr || airDate > todayStr) continue

                        val isWatched = watchedMap["${seasonNumber}_${ep.episodeNumber}"] ?: false
                        if (isWatched) continue

                        notificationRepository.saveNotification(
                            userId = userId,
                            mediaId = internalMediaId,
                            seasonNumber = seasonNumber,
                            episodeNumber = ep.episodeNumber,
                            airDate = ep.airDate
                        )
                    }
                }
            }

            // 2. Обработка фильмов со статусом "В планах" (PLANNED)
            for (movie in plannedMovies) {
                val catalogId = movie.catalogId.ifEmpty { "tmdb" }
                val internalMediaId = notificationRepository.getOrCreateMediaId(catalogId, movie.mediaId, "movie")

                val mediaKey = MediaKey(MediaProvider.Tmdb, EntityType.MOVIE, movie.mediaId)
                val mediaDetails = runCatching {
                    catalog.getMediaDetails(mediaKey, requireSeasons = false, requireVideos = false, language = "ru")
                }.getOrNull() ?: continue

                val releaseDate = mediaDetails.releaseDate?.takeIf { it.isNotBlank() } ?: continue

                // Фильм должен уже выйти к текущему моменту
                if (releaseDate > todayStr) continue

                // Фильм вышел после момента добавления в "Буду смотреть" (или за 3 дня до отметки)
                val addedAt = if (movie.createdAt != Instant.DISTANT_PAST) movie.createdAt else now
                val cutoffDateStr = (addedAt - 3.days).toString().substringBefore("T")
                if (releaseDate < cutoffDateStr) {
                    // Старый фильм, добавленный ретроспективно — не шлем уведомление
                    continue
                }

                notificationRepository.saveMovieNotification(
                    userId = userId,
                    mediaId = internalMediaId,
                    releaseDate = releaseDate
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to sync notifications for user $userId", e)
        }
    }

    private suspend fun syncAllUsers() {
        val users = userRepository.getAllUsers()
        for (user in users) {
            syncUser(user.id)
        }
    }
}
