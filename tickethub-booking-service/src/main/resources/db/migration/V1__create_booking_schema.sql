-- Application booking statuses: PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, EXPIRED, CANCELLED, REFUND_PENDING, REFUNDED.
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    booking_reference VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    payment_reference VARCHAR(100) NULL,
    customer_email VARCHAR(255) NULL,
    customer_name VARCHAR(150) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_booking_reference UNIQUE (booking_reference),
    INDEX idx_booking_user (user_id),
    INDEX idx_booking_show (show_id),
    INDEX idx_booking_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS booking_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_number VARCHAR(30) NULL,
    price DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_items_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT uk_booking_item_seat UNIQUE (booking_id, seat_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS seat_locks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    locked_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_lock_show_seat UNIQUE (show_id, seat_id),
    INDEX idx_lock_show (show_id),
    INDEX idx_lock_expiry (expires_at),
    INDEX idx_lock_booking (booking_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    ticket_number VARCHAR(50) NOT NULL,
    qr_code VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ticket_booking UNIQUE (booking_id),
    CONSTRAINT uk_ticket_number UNIQUE (ticket_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created (created_at)
) ENGINE=InnoDB;
