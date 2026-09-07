package org.ensodai.avalonmediacard.database

import org.ensodai.avalonmediacard.contract.model.NotificationType
import org.jetbrains.exposed.v1.core.ReferenceOption

object UserEpisodeNotificationTable : BaseUuidTable("user_episode_notifications") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val mediaId = reference("media_id", MediaTable, onDelete = ReferenceOption.CASCADE)
    val notificationType = enumerationByName("notification_type", 30, NotificationType::class).default(NotificationType.EPISODE_RELEASE)
    val targetKey = varchar("target_key", 50)
    val seasonNumber = integer("season_number").nullable()
    val episodeNumber = integer("episode_number").nullable()
    val airDate = varchar("air_date", 50).nullable()
    val isRead = bool("is_read").default(false)
    val isDismissed = bool("is_dismissed").default(false)

    init {
        uniqueIndex("user_ep_notif_target_unique", userId, mediaId, targetKey)
        index("user_ep_notif_user_read", false, userId, isRead, isDismissed)
    }
}
