-- ---------------------------------------------------------------------------
-- Cheaply V2 - baseline schema.
--
-- This replaces Hibernate's `ddl-auto: update`. Every future schema change
-- must arrive as a new, immutable V<n>__description.sql file; never edit a
-- migration that has already been applied to any environment.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

-- Uniqueness is enforced case-insensitively and in the database.
--
-- A plain UNIQUE (username) would happily accept "Admin" alongside an existing
-- "admin", which is an impersonation vector, and application-level existsBy
-- checks lose to concurrent signups. A functional unique index closes both.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower    ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS search_histories (
    id          BIGSERIAL    PRIMARY KEY,
    query       VARCHAR(120) NOT NULL,
    searched_at TIMESTAMP    NOT NULL,
    user_id     BIGINT       NOT NULL,
    CONSTRAINT fk_search_histories_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Serves findByUserOrderBySearchedAtDesc, which is on the hot path of every
-- authenticated search.
CREATE INDEX IF NOT EXISTS idx_search_histories_user_searched_at
    ON search_histories (user_id, searched_at DESC);

-- The service treats (user, query) as unique and case-insensitive. Enforcing
-- that here too closes the race where two concurrent searches by the same user
-- both miss the SELECT and both INSERT.
CREATE UNIQUE INDEX IF NOT EXISTS uk_search_histories_user_query
    ON search_histories (user_id, LOWER(query));
