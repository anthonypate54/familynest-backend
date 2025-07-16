-- V26: Add device permission tracking for Firebase notification permissions
-- This tracks whether the user has granted device-level notification permission

-- Add device permission column to user_notification_settings
ALTER TABLE user_notification_settings 
ADD COLUMN device_permission_granted BOOLEAN DEFAULT FALSE NOT NULL;

-- Add comment for clarity
COMMENT ON COLUMN user_notification_settings.device_permission_granted 
IS 'Tracks whether user granted device-level notification permission (Firebase/iOS Settings)';

-- Update existing users to FALSE (conservative approach - they need to re-grant permission)
UPDATE user_notification_settings 
SET device_permission_granted = FALSE 
WHERE device_permission_granted IS NULL; 