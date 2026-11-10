-- short_code uses a binary (case-sensitive) collation on purpose:
-- Base62 codes such as "aB3xYz" and "ab3xyz" are different links.
-- MySQL's default collation is case-insensitive and would treat them as duplicates.
CREATE TABLE short_url (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    short_code   VARCHAR(30)   CHARACTER SET ascii COLLATE ascii_bin NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    expires_at   DATETIME(6)   NULL,
    click_count  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_short_url_code UNIQUE (short_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
