CREATE TABLE user_episode_notifications_new
(
    id                VARCHAR(36)                         NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id           VARCHAR(36)                         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_id          VARCHAR(36)                         NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    notification_type VARCHAR(30) DEFAULT 'EPISODE_RELEASE' NOT NULL,
    target_key        VARCHAR(50)                         NOT NULL,
    show_title        VARCHAR(255)                        NOT NULL,
    show_poster_url   VARCHAR(500)                        NULL,
    season_number     INT                                 NULL,
    episode_number    INT                                 NULL,
    episode_title     VARCHAR(255)                        NULL,
    air_date          VARCHAR(50)                         NULL,
    still_url         VARCHAR(500)                        NULL,
    overview          TEXT                                NULL,
    duration_minutes  INT                                 NULL,
    is_read           BOOLEAN   DEFAULT 0                 NOT NULL,
    is_dismissed      BOOLEAN   DEFAULT 0                 NOT NULL
);

INSERT INTO user_episode_notifications_new (
    id, created_at, updated_at, user_id, media_id, notification_type, target_key,
    show_title, show_poster_url, season_number, episode_number, episode_title,
    air_date, still_url, overview, duration_minutes, is_read, is_dismissed
)
SELECT
    id, created_at, updated_at, user_id, media_id, 'EPISODE_RELEASE',
    's' || season_number || '_e' || episode_number,
    show_title, show_poster_url, season_number, episode_number, episode_title,
    air_date, still_url, overview, duration_minutes, is_read, is_dismissed
FROM user_episode_notifications;

DROP TABLE user_episode_notifications;
ALTER TABLE user_episode_notifications_new RENAME TO user_episode_notifications;

CREATE UNIQUE INDEX IF NOT EXISTS user_ep_notif_target_unique ON user_episode_notifications (user_id, media_id, target_key);
CREATE INDEX IF NOT EXISTS user_ep_notif_user_read ON user_episode_notifications (user_id, is_read, is_dismissed);
