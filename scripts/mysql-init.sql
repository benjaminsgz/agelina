CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users (id, username, password)
VALUES (1, 'demo', '$2b$10$A5nYjhDF49VAtD7pUE5lp.pKdBwEEUYpxFfo/yRIXujiWAadt/YVS') AS new
ON DUPLICATE KEY UPDATE password = new.password;
