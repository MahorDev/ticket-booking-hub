CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    event_key VARCHAR(180) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT uk_notification_event_key UNIQUE (event_key),
    INDEX idx_notification_booking_id (booking_id),
    INDEX idx_notification_status (status)
);
