CREATE TABLE IF NOT EXISTS payments (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        booking_id BIGINT NOT NULL,
                                        amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    razorpay_order_id VARCHAR(100) NULL,
    razorpay_payment_id VARCHAR(100) NULL,
    razorpay_signature VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_payments_idempotency_key
    UNIQUE (idempotency_key),

    CONSTRAINT uk_payments_razorpay_order_id
    UNIQUE (razorpay_order_id),

    CONSTRAINT uk_payments_razorpay_payment_id
    UNIQUE (razorpay_payment_id),

    INDEX idx_payment_booking_id (booking_id),
    INDEX idx_payment_razorpay_order_id (razorpay_order_id),
    INDEX idx_payment_razorpay_payment_id (razorpay_payment_id)
    );


CREATE TABLE IF NOT EXISTS payment_webhook_events (
                                                      id BIGINT NOT NULL AUTO_INCREMENT,

                                                      event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,

    payment_id BIGINT NULL,

    razorpay_payment_id VARCHAR(100) NULL,
    razorpay_order_id VARCHAR(100) NULL,

    payload LONGTEXT NOT NULL,

    processed BOOLEAN NOT NULL DEFAULT FALSE,

    processing_error VARCHAR(1000) NULL,

    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_payment_webhook_event_id
    UNIQUE (event_id),

    INDEX idx_webhook_payment_id (payment_id),
    INDEX idx_webhook_razorpay_payment_id (razorpay_payment_id),
    INDEX idx_webhook_razorpay_order_id (razorpay_order_id)
    );