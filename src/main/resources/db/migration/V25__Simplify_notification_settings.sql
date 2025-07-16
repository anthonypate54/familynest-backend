-- V25: Simplify notification settings to just push and email notifications
-- Remove complex granular settings and replace with simple global settings

-- Drop the complex notification tables created in V23
DROP TABLE IF EXISTS user_dm_notification_settings CASCADE;
DROP TABLE IF EXISTS user_global_notification_settings CASCADE; 
DROP TABLE IF EXISTS user_invitation_notification_settings CASCADE;

-- Drop the trigger and function from V23
DROP TRIGGER IF EXISTS create_notification_settings_for_new_user ON app_user;
DROP FUNCTION IF EXISTS create_default_notification_settings();

-- Create simple notification settings table (defaulting to FALSE for opt-in approach)
CREATE TABLE user_notification_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    push_notifications_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    email_notifications_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create index for performance
CREATE INDEX idx_notification_settings_user ON user_notification_settings(user_id);

-- Function to create default notification settings for new users
CREATE OR REPLACE FUNCTION create_simple_notification_settings()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO user_notification_settings (user_id)
    VALUES (NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-create notification settings for new users
CREATE TRIGGER create_simple_notification_settings_for_new_user
    AFTER INSERT ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION create_simple_notification_settings();

-- Create default settings for all existing users (defaulting to disabled for opt-in approach)
INSERT INTO user_notification_settings (user_id, push_notifications_enabled, email_notifications_enabled)
SELECT id, FALSE, FALSE FROM app_user;

-- Create updated_at trigger function if it doesn't exist
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update updated_at timestamp
CREATE TRIGGER update_notification_settings_updated_at
    BEFORE UPDATE ON user_notification_settings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column(); 