CREATE TABLE IF NOT EXISTS user_episode_notifications
(
    id               VARCHAR(36)                         NOT NULL PRIMARY KEY,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id          VARCHAR(36)                         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_id         VARCHAR(36)                         NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    show_title       VARCHAR(255)                        NOT NULL,
    show_poster_url  VARCHAR(500)                        NULL,
    season_number    INT                                 NOT NULL,
    episode_number   INT                                 NOT NULL,
    episode_title    VARCHAR(255)                        NOT NULL,
    air_date         VARCHAR(50)                         NULL,
    still_url        VARCHAR(500)                        NULL,
    overview         TEXT                                NULL,
    duration_minutes INT                                 NULL,
    is_read          BOOLEAN   DEFAULT 0                 NOT NULL,
    is_dismissed     BOOLEAN   DEFAULT 0                 NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS user_ep_notif_unique ON user_episode_notifications (user_id, media_id, season_number, episode_number);
CREATE INDEX IF NOT EXISTS user_ep_notif_user_read ON user_episode_notifications (user_id, is_read, is_dismissed);
