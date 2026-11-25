-- Events waiting to be published. A row is written in the same transaction as the change it
-- describes, and deleted once the broker has acknowledged it.
CREATE TABLE outbox (
    id          bigserial PRIMARY KEY,
    message_key varchar(255) NOT NULL,
    payload     text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
