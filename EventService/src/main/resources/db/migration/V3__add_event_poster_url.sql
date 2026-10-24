-- Idempotent column addition for poster_url if table already exists in existing volume
-- In MySQL 8.0+, we can use procedure or alter table if exists
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'events' 
    AND COLUMN_NAME = 'poster_url');

SET @query := IF(@exist = 0, 'ALTER TABLE events ADD COLUMN poster_url VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
