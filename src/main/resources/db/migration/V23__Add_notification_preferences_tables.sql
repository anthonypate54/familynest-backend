-- Add DM notification settings table
CREATE TABLE user_dm_notification_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    receive_dm_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    email_dm_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    push_dm_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dm_notifications_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Add global notification settings table
CREATE TABLE user_global_notification_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    email_notifications_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    push_notifications_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    quiet_hours_start TIME DEFAULT '22:00:00' NOT NULL,
    quiet_hours_end TIME DEFAULT '08:00:00' NOT NULL,
    weekend_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_global_notifications_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Add invitation notification settings table
CREATE TABLE user_invitation_notification_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    receive_invitation_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    email_invitation_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    push_invitation_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    notify_on_invitation_accepted BOOLEAN DEFAULT TRUE NOT NULL,
    notify_on_invitation_declined BOOLEAN DEFAULT FALSE NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitation_notifications_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_dm_notifications_user ON user_dm_notification_settings(user_id);
CREATE INDEX idx_global_notifications_user ON user_global_notification_settings(user_id);
CREATE INDEX idx_invitation_notifications_user ON user_invitation_notification_settings(user_id);

-- Function to create default notification settings for new users
CREATE OR REPLACE FUNCTION create_default_notification_settings()
RETURNS TRIGGER AS $$
BEGIN
    -- Create default DM notification settings
    INSERT INTO user_dm_notification_settings (user_id)
    VALUES (NEW.id);
    
    -- Create default global notification settings
    INSERT INTO user_global_notification_settings (user_id)
    VALUES (NEW.id);
    
    -- Create default invitation notification settings
    INSERT INTO user_invitation_notification_settings (user_id)
    VALUES (NEW.id);
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-create notification settings for new users
CREATE TRIGGER create_notification_settings_for_new_user
    AFTER INSERT ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION create_default_notification_settings();

-- Create default settings for existing users
INSERT INTO user_dm_notification_settings (user_id)
SELECT id FROM app_user WHERE id NOT IN (SELECT user_id FROM user_dm_notification_settings);

INSERT INTO user_global_notification_settings (user_id)
SELECT id FROM app_user WHERE id NOT IN (SELECT user_id FROM user_global_notification_settings);

INSERT INTO user_invitation_notification_settings (user_id)
SELECT id FROM app_user WHERE id NOT IN (SELECT user_id FROM user_invitation_notification_settings); 