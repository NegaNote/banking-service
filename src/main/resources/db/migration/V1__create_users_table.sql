CREATE TABLE users (
                       id           BIGINT          NOT NULL AUTO_INCREMENT,
                       username     VARCHAR(50)     NOT NULL,
                       email        VARCHAR(120)    NOT NULL,
                       password_hash     VARCHAR(100)    NOT NULL,
                       role         enum ('ADMIN','USER')     NOT NULL,
                       created_at   DATETIME(6)     NOT NULL,
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_username (username),
                       UNIQUE KEY uk_users_email    (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;