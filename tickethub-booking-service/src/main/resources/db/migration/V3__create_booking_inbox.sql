CREATE TABLE IF NOT EXISTS inbox_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(120) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    booking_id BIGINT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 1,
    payload TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSED',
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_inbox_event_id UNIQUE (event_id),
    INDEX idx_booking_inbox_booking_id (booking_id)
) ENGINE=InnoDB;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS aggregate_version BIGINT NOT NULL DEFAULT 1;
