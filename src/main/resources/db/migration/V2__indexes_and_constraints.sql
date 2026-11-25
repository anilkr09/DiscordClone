-- Message history is read newest first within a channel; the id breaks ties for cursor paging.
CREATE INDEX idx_messages_channel_ts ON messages (channel_id, "timestamp" DESC, id DESC);
CREATE INDEX idx_members_user ON server_members (user_id);

-- At most one friendship row per pair of users, whichever of them started it. Where older data
-- holds more, keep the most restrictive and then the most recent.
DELETE FROM friendships WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)
            ORDER BY CASE status WHEN 'BLOCKED' THEN 0 WHEN 'ACCEPTED' THEN 1 WHEN 'PENDING' THEN 2 ELSE 3 END,
                     updated_at DESC, id DESC) AS position
        FROM friendships) ranked
    WHERE position > 1);
CREATE UNIQUE INDEX uq_friendship_pair
    ON friendships (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id));
