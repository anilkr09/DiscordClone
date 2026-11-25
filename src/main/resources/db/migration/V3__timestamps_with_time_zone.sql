-- Existing values were written with the server's local clock. They are read as UTC, which is what
-- the container images and hosted deployments use.
ALTER TABLE messages ALTER COLUMN "timestamp" TYPE timestamptz(6) USING "timestamp" AT TIME ZONE 'UTC';
ALTER TABLE friendships
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE invites ALTER COLUMN expiry TYPE timestamptz(6) USING expiry AT TIME ZONE 'UTC';
ALTER TABLE server_members ALTER COLUMN joined_at TYPE timestamptz(6) USING joined_at AT TIME ZONE 'UTC';
ALTER TABLE user_status
    ALTER COLUMN status_expires_at TYPE timestamptz(6) USING status_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_activity TYPE timestamptz(6) USING last_activity AT TIME ZONE 'UTC';
