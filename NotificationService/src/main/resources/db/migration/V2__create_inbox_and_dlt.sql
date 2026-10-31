CREATE TABLE IF NOT EXISTS inbox_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED',
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inbox_event_id UNIQUE (event_id),
    INDEX idx_inbox_aggregate (aggregate_id)
);

CREATE TABLE IF NOT EXISTS dlt_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic VARCHAR(128) NOT NULL,
    original_topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NULL,
    payload LONGTEXT NOT NULL,
    exception_message VARCHAR(2000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'FAILED',
    created_at DATETIME(6) NOT NULL,
    reprocessed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_dlt_status (status)
);
