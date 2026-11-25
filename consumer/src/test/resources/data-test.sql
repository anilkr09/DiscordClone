INSERT INTO users (id, username, email, password) VALUES (1, 'a', 'a@example.com', 'x') ON CONFLICT DO NOTHING;
INSERT INTO channels (id, name, type) VALUES (1, 'general', 'TEXT') ON CONFLICT DO NOTHING;
