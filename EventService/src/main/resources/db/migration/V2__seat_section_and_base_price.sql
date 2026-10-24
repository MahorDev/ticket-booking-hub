-- V2__seat_section_and_base_price.sql
-- Safe for repeated Spring Boot SQL initialization.
-- Adds columns only when they do not already exist.

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE seats ADD COLUMN section VARCHAR(20) NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seats'
      AND column_name = 'section'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE seats ADD COLUMN base_price DECIMAL(10,2) NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seats'
      AND column_name = 'base_price'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;