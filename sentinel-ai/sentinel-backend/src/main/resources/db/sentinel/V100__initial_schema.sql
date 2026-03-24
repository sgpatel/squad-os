-- SentinelAI v1.0 Initial Schema

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(50)  NOT NULL DEFAULT 'REVIEWER',
    tenant_id     VARCHAR(36)  NOT NULL DEFAULT 'default',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mentions (
    id                 VARCHAR(50)   PRIMARY KEY,
    platform           VARCHAR(50)   NOT NULL DEFAULT 'TWITTER',
    handle             VARCHAR(100)  NOT NULL,
    tenant_id          VARCHAR(36)   NOT NULL DEFAULT 'default',
    author_username    VARCHAR(100),
    author_name        VARCHAR(255),
    author_followers   BIGINT        DEFAULT 0,
    text               TEXT,
    original_text      TEXT,
    language           VARCHAR(10)   DEFAULT 'en',
    posted_at          TIMESTAMP,
    ingested_at        TIMESTAMP,
    url                VARCHAR(1000),
    like_count         BIGINT        DEFAULT 0,
    retweet_count      BIGINT        DEFAULT 0,
    sentiment_label    VARCHAR(20),
    sentiment_score    DOUBLE PRECISION DEFAULT 0.5,
    primary_emotion    VARCHAR(50),
    urgency            VARCHAR(20),
    topic              VARCHAR(100),
    summary            VARCHAR(500),
    priority           VARCHAR(20),
    escalation_path    VARCHAR(100),
    assigned_team      VARCHAR(100),
    ticket_id          VARCHAR(100),
    ticket_system      VARCHAR(50),
    ticket_status      VARCHAR(50),
    reply_text         TEXT,
    reply_status       VARCHAR(30)   DEFAULT 'NONE',
    processing_status  VARCHAR(30)   DEFAULT 'NEW',
    urgency_score      INTEGER       DEFAULT 0,
    viral_risk_score   INTEGER       DEFAULT 0,
    is_viral           BOOLEAN       DEFAULT FALSE,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mentions_handle         ON mentions(handle);
CREATE INDEX IF NOT EXISTS idx_mentions_sentiment      ON mentions(sentiment_label);
CREATE INDEX IF NOT EXISTS idx_mentions_posted_at      ON mentions(posted_at DESC);
CREATE INDEX IF NOT EXISTS idx_mentions_priority       ON mentions(priority);
CREATE INDEX IF NOT EXISTS idx_mentions_reply_status   ON mentions(reply_status);
CREATE INDEX IF NOT EXISTS idx_mentions_processing     ON mentions(processing_status);
CREATE INDEX IF NOT EXISTS idx_mentions_tenant         ON mentions(tenant_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
