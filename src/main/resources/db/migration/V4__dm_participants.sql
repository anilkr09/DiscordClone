CREATE TABLE channel_members (
    channel_id bigint NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    user_id    bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (channel_id, user_id)
);
CREATE INDEX idx_channel_members_user ON channel_members (user_id);

-- Existing DMs record their participants only in dm_key, "dm-{minId}-{maxId}".
INSERT INTO channel_members (channel_id, user_id)
SELECT DISTINCT c.id, u.id
FROM channels c
JOIN users u ON u.id IN (split_part(c.dm_key, '-', 2)::bigint, split_part(c.dm_key, '-', 3)::bigint)
WHERE c.type = 'DM' AND c.dm_key ~ '^dm-[0-9]{1,18}-[0-9]{1,18}$';

-- DMs no longer belong to server 1.
UPDATE channels SET server_id = NULL WHERE type = 'DM';

DROP TABLE dm_channels;
