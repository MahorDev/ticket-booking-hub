CREATE TABLE IF NOT EXISTS events (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    event_type VARCHAR(30) NOT NULL,
    language VARCHAR(50) NOT NULL,
    duration INT NOT NULL,
    rating VARCHAR(20),
    genre VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    poster_url VARCHAR(500),
    PRIMARY KEY (event_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS venues (
    venue_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    country VARCHAR(80) NOT NULL,
    PRIMARY KEY (venue_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS screens (
    screen_id BIGINT NOT NULL AUTO_INCREMENT,
    screen_name VARCHAR(100) NOT NULL,
    venue_id BIGINT NOT NULL,
    PRIMARY KEY (screen_id),
    CONSTRAINT uk_screen_venue_name UNIQUE (venue_id, screen_name),
    CONSTRAINT fk_screen_venue FOREIGN KEY (venue_id) REFERENCES venues (venue_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS seats (
    seat_id BIGINT NOT NULL AUTO_INCREMENT,
    seat_number VARCHAR(30) NOT NULL,
    row_name VARCHAR(20) NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    section VARCHAR(20) NOT NULL,
    base_price DECIMAL(10,2),
    screen_id BIGINT NOT NULL,
    PRIMARY KEY (seat_id),
    CONSTRAINT uk_seat_screen_number UNIQUE (screen_id, seat_number),
    CONSTRAINT fk_seat_screen FOREIGN KEY (screen_id) REFERENCES screens (screen_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shows (
    show_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    screen_id BIGINT NOT NULL,
    show_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    ticket_price DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (show_id),
    KEY idx_show_event (event_id),
    KEY idx_show_screen_date (screen_id, show_date),
    CONSTRAINT fk_show_event FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT fk_show_screen FOREIGN KEY (screen_id) REFERENCES screens (screen_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS show_seats (
    show_seat_id BIGINT NOT NULL AUTO_INCREMENT,
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (show_seat_id),
    CONSTRAINT uk_show_seat UNIQUE (show_id, seat_id),
    KEY idx_show_seat_show (show_id),
    KEY idx_show_seat_seat (seat_id),
    CONSTRAINT fk_show_seat_show FOREIGN KEY (show_id) REFERENCES shows (show_id),
    CONSTRAINT fk_show_seat_seat FOREIGN KEY (seat_id) REFERENCES seats (seat_id)
) ENGINE=InnoDB;
