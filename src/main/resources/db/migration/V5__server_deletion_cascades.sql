-- Deleting a server removes its members, invites, channels and their messages.
-- This runs after V4, which detaches DMs from servers, so no conversation is deleted with one.
ALTER TABLE server_members
    DROP CONSTRAINT IF EXISTS fkqu0vrc783yq288y2r92gjurw2,
    ADD CONSTRAINT fk_members_server FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE;
ALTER TABLE channels
    DROP CONSTRAINT IF EXISTS fkacxri6fsdtdwloddqhewkt62w,
    ADD CONSTRAINT fk_channels_server FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE;
ALTER TABLE invites
    DROP CONSTRAINT IF EXISTS fkg3nma3og6xh0aqtnb75434q3c,
    ADD CONSTRAINT fk_invites_server FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE;
ALTER TABLE messages
    DROP CONSTRAINT IF EXISTS fk3u3ckbhwq9se1cmopk2pq05b2,
    ADD CONSTRAINT fk_messages_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE;
