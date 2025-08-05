-- V32: Create user preferences table and cleanup unused notification tables

-- Create new user_preferences table for all user settings
CREATE TABLE user_preferences (
    user_id BIGINT NOT NULL PRIMARY KEY,
    
    -- Demographics/Privacy Settings
    show_address BOOLEAN DEFAULT TRUE NOT NULL,
    show_phone_number BOOLEAN DEFAULT TRUE NOT NULL,
    show_birthday BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Notification Preferences  
    family_messages_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    new_member_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    invitation_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create index for better performance
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

-- Migrate existing show_demographics data from app_user table
INSERT INTO user_preferences (user_id, show_address, show_phone_number, show_birthday)
SELECT 
    id as user_id,
    COALESCE(show_demographics, TRUE) as show_address,
    COALESCE(show_demographics, TRUE) as show_phone_number,
    COALESCE(show_demographics, TRUE) as show_birthday
FROM app_user
ON CONFLICT (user_id) DO NOTHING;

-- Remove the old show_demographics column from app_user
ALTER TABLE app_user DROP COLUMN IF EXISTS show_demographics;

-- Drop unused notification tables that were never implemente
DROP TABLE IF EXISTS user_dm_notification_settings;
DROP TABLE IF EXISTS user_global_notification_settings;  
DROP TABLE IF EXISTS user_invitation_notification_settings;

-- Note: We keep user_member_message_settings and user_family_message_settings 
-- as they are actively used for muting functionality