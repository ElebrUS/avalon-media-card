package org.ensodai.avalonmediacard.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.ensodai.avalonmediacard.contract.model.EntityType
import org.ensodai.avalonmediacard.contract.model.MediaKey
import org.ensodai.avalonmediacard.contract.model.MediaProvider
import org.ensodai.avalonmediacard.contract.model.NotificationType
import org.ensodai.avalonmediacard.contract.plugins.EpisodeNotificationItemDto
import org.ensodai.avalonmediacard.contract.plugins.UserEpisodeNotificationProvider
import org.ensodai.avalonmediacard.database.MediaTable
import org.ensodai.avalonmediacard.database.UserEpisodeNotificationTable
import org.ensodai.avalonmediacard.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Single
class UserEpisodeNotificationRepository : UserEpisodeNotificationProvider {

    private val changeEvents = MutableSharedFlow<Uuid>(extraBufferCapacity = 64)

    fun notifyUpdate(userId: Uuid) {
        changeEvents.tryEmit(userId)
    }

    override fun observeUnreadCount(userId: Uuid): Flow<Int> =
        changeEvents.filter { it == userId }
            .onStart { emit(userId) }
            .map { getUnreadCount(userId) }

    suspend fun getUnreadCount(userId: Uuid): Int = dbQuery {
        UserEpisodeNotificationTable
            .selectAll()
            .where {
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.isRead eq false) and
                (UserEpisodeNotificationTable.isDismissed eq false)
            }
            .count()
            .toInt()
    }

    override suspend fun getNotifications(userId: Uuid, limit: Int): List<EpisodeNotificationItemDto> = dbQuery {
        (UserEpisodeNotificationTable innerJoin MediaTable)
            .selectAll()
            .where {
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.isDismissed eq false)
            }
            .orderBy(
                UserEpisodeNotificationTable.airDate to SortOrder.DESC_NULLS_LAST,
                UserEpisodeNotificationTable.createdAt to SortOrder.DESC
            )
            .limit(limit)
            .map { toDto(it) }
    }

    override suspend fun markAllAsRead(userId: Uuid) {
        dbQuery {
            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.isRead eq false)
            }) {
                it[isRead] = true
            }
        }
        notifyUpdate(userId)
    }

    override suspend fun markAsRead(userId: Uuid, notificationId: Uuid) {
        dbQuery {
            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.id eq notificationId)
            }) {
                it[isRead] = true
            }
        }
        notifyUpdate(userId)
    }

    override suspend fun dismiss(userId: Uuid, notificationId: Uuid) {
        dbQuery {
            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.id eq notificationId)
            }) {
                it[isDismissed] = true
            }
        }
        notifyUpdate(userId)
    }

    suspend fun markWatched(userId: Uuid, mediaId: Uuid, seasonNumber: Int, episodeNumber: Int) {
        val targetKey = "s${seasonNumber}_e${episodeNumber}"
        dbQuery {
            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.mediaId eq mediaId) and
                (UserEpisodeNotificationTable.targetKey eq targetKey)
            }) {
                it[isRead] = true
                it[isDismissed] = true
            }
        }
        notifyUpdate(userId)
    }

    override suspend fun markWatched(
        userId: Uuid,
        catalogId: String,
        externalId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val catId = catalogId.ifEmpty { "tmdb" }
        val targetKey = "s${seasonNumber}_e${episodeNumber}"
        dbQuery {
            val internalMediaId = MediaTable.selectAll()
                .where { (MediaTable.catalogId eq catId) and (MediaTable.externalId eq externalId) }
                .firstOrNull()?.get(MediaTable.id)?.value ?: return@dbQuery

            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.mediaId eq internalMediaId) and
                (UserEpisodeNotificationTable.targetKey eq targetKey)
            }) {
                it[isRead] = true
                it[isDismissed] = true
            }
        }
        notifyUpdate(userId)
    }

    suspend fun markMovieWatched(userId: Uuid, mediaId: Uuid) {
        dbQuery {
            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.mediaId eq mediaId) and
                (UserEpisodeNotificationTable.targetKey eq "movie")
            }) {
                it[isRead] = true
                it[isDismissed] = true
            }
        }
        notifyUpdate(userId)
    }

    override suspend fun markMovieWatched(userId: Uuid, catalogId: String, externalId: String) {
        val catId = catalogId.ifEmpty { "tmdb" }
        dbQuery {
            val internalMediaId = MediaTable.selectAll()
                .where { (MediaTable.catalogId eq catId) and (MediaTable.externalId eq externalId) }
                .firstOrNull()?.get(MediaTable.id)?.value ?: return@dbQuery

            UserEpisodeNotificationTable.update({
                (UserEpisodeNotificationTable.userId eq userId) and
                (UserEpisodeNotificationTable.mediaId eq internalMediaId) and
                (UserEpisodeNotificationTable.targetKey eq "movie")
            }) {
                it[isRead] = true
                it[isDismissed] = true
            }
        }
        notifyUpdate(userId)
    }

    suspend fun getOrCreateMediaId(catalogId: String, externalId: String, mediaType: String): Uuid = dbQuery {
        val catId = catalogId.ifEmpty { "tmdb" }
        val existing = MediaTable.selectAll()
            .where { (MediaTable.catalogId eq catId) and (MediaTable.externalId eq externalId) }
            .firstOrNull()?.get(MediaTable.id)?.value

        if (existing != null) return@dbQuery existing

        val newId = Uuid.random()
        MediaTable.insertIgnore {
            it[id] = newId
            it[this.catalogId] = catId
            it[this.externalId] = externalId
            it[this.mediaType] = mediaType
        }
        MediaTable.selectAll()
            .where { (MediaTable.catalogId eq catId) and (MediaTable.externalId eq externalId) }
            .first()[MediaTable.id].value
    }

    suspend fun saveNotification(
        userId: Uuid,
        mediaId: Uuid,
        seasonNumber: Int,
        episodeNumber: Int,
        airDate: String?
    ) {
        val targetKey = "s${seasonNumber}_e${episodeNumber}"
        dbQuery {
            UserEpisodeNotificationTable.upsert(
                UserEpisodeNotificationTable.userId,
                UserEpisodeNotificationTable.mediaId,
                UserEpisodeNotificationTable.targetKey
            ) {
                it[UserEpisodeNotificationTable.userId] = userId
                it[UserEpisodeNotificationTable.mediaId] = mediaId
                it[UserEpisodeNotificationTable.notificationType] = NotificationType.EPISODE_RELEASE
                it[UserEpisodeNotificationTable.targetKey] = targetKey
                it[UserEpisodeNotificationTable.seasonNumber] = seasonNumber
                it[UserEpisodeNotificationTable.episodeNumber] = episodeNumber
                it[UserEpisodeNotificationTable.airDate] = airDate
            }
        }
        notifyUpdate(userId)
    }

    suspend fun saveMovieNotification(
        userId: Uuid,
        mediaId: Uuid,
        releaseDate: String?
    ) {
        dbQuery {
            UserEpisodeNotificationTable.upsert(
                UserEpisodeNotificationTable.userId,
                UserEpisodeNotificationTable.mediaId,
                UserEpisodeNotificationTable.targetKey
            ) {
                it[UserEpisodeNotificationTable.userId] = userId
                it[UserEpisodeNotificationTable.mediaId] = mediaId
                it[UserEpisodeNotificationTable.notificationType] = NotificationType.MOVIE_RELEASE
                it[UserEpisodeNotificationTable.targetKey] = "movie"
                it[UserEpisodeNotificationTable.seasonNumber] = null
                it[UserEpisodeNotificationTable.episodeNumber] = null
                it[UserEpisodeNotificationTable.airDate] = releaseDate
            }
        }
        notifyUpdate(userId)
    }

    override suspend fun syncNotifications(userId: Uuid) {
        // Handled by sync worker
    }

    private fun toDto(row: ResultRow): EpisodeNotificationItemDto {
        val catalogId = row[MediaTable.catalogId]
        val externalId = row[MediaTable.externalId]
        val rawMediaType = row[MediaTable.mediaType]
        val entityType = if (rawMediaType.equals("movie", ignoreCase = true)) EntityType.MOVIE else EntityType.TV
        val mediaProvider = when (catalogId.lowercase()) {
            "tmdb" -> MediaProvider.Tmdb
            else -> MediaProvider.Tmdb
        }
        return EpisodeNotificationItemDto(
            id = row[UserEpisodeNotificationTable.id].value,
            userId = row[UserEpisodeNotificationTable.userId],
            mediaKey = MediaKey(mediaProvider, entityType, externalId),
            seasonNumber = row[UserEpisodeNotificationTable.seasonNumber],
            episodeNumber = row[UserEpisodeNotificationTable.episodeNumber],
            airDate = row[UserEpisodeNotificationTable.airDate],
            isRead = row[UserEpisodeNotificationTable.isRead],
            isDismissed = row[UserEpisodeNotificationTable.isDismissed],
            createdAt = row[UserEpisodeNotificationTable.createdAt],
            notificationType = row[UserEpisodeNotificationTable.notificationType]
        )
    }
}
