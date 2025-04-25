-- Add sender_username column to message table if it doesn't exist
ALTER TABLE message ADD COLUMN IF NOT EXISTS sender_username VARCHAR(255);

-- Populate sender_username from app_user where possible
UPDATE message m
SET sender_username = u.username
FROM app_user u
WHERE m.sender_id = u.id AND m.sender_username IS NULL; 