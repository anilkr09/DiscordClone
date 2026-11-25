-- New usernames and emails are unique regardless of case. Older data may already hold names that
-- differ only by case; then the index is skipped with a notice rather than failing the start-up.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users GROUP BY lower(username) HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'usernames differing only by case exist; uq_users_username_lower not created';
    ELSE
        CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
    END IF;
    IF EXISTS (SELECT 1 FROM users GROUP BY lower(email) HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'emails differing only by case exist; uq_users_email_lower not created';
    ELSE
        CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
    END IF;
END $$;
