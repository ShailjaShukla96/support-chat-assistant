-- Schema from specs/001-support-chat-assistant/data-model.md.
-- No column stores API keys, the system prompt, or provider error bodies.
-- Column notes: "value" and "text" are renamed (rating_value, message_text) to avoid H2 reserved words.

CREATE TABLE IF NOT EXISTS chat_session (
    id          UUID PRIMARY KEY,
    user_ref    VARCHAR(128),
    created_at  TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id                  UUID PRIMARY KEY,
    session_id          UUID NOT NULL,
    role                VARCHAR(16) NOT NULL,
    message_text        CLOB NOT NULL,
    outcome             VARCHAR(16),
    escalation_offered  BOOLEAN NOT NULL,
    request_id          VARCHAR(64),
    latency_ms          INTEGER,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id)
);
CREATE INDEX IF NOT EXISTS idx_message_session_created ON message (session_id, created_at);

CREATE TABLE IF NOT EXISTS escalation_request (
    id          UUID PRIMARY KEY,
    session_id  UUID NOT NULL,
    reason      VARCHAR(32) NOT NULL,
    transcript  CLOB NOT NULL,
    status      VARCHAR(16) NOT NULL,
    request_id  VARCHAR(64),
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_escalation_session FOREIGN KEY (session_id) REFERENCES chat_session (id)
);
CREATE INDEX IF NOT EXISTS idx_escalation_created ON escalation_request (created_at);

CREATE TABLE IF NOT EXISTS response_rating (
    id            UUID PRIMARY KEY,
    message_id    UUID NOT NULL UNIQUE,
    rating_value  VARCHAR(8) NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_rating_message FOREIGN KEY (message_id) REFERENCES message (id)
);
