-- V35: Add notification preference columns to user_notification_settings
-- This adds individual toggles for different notification types

-- Add new member notifications column
ALTER TABLE user_notification_settings 
ADD COLUMN new_member_notifications BOOLEAN NOT NULL DEFAULT TRUE;

-- Add invitation notifications column  
ALTER TABLE user_notification_settings 
ADD COLUMN invitation_notifications BOOLEAN NOT NULL DEFAULT TRUE;

-- Update the comment for the table
COMMENT ON TABLE user_notification_settings IS 'User notification preferences including device permissions and individual notification type toggles';

-- Add comments for the new columns
COMMENT ON COLUMN user_notification_settings.new_member_notifications IS 'Whether user wants notifications when someone joins their family';
COMMENT ON COLUMN user_notification_settings.invitation_notifications IS 'Whether user wants notifications when they receive family invitations';

-- Create index for efficient notification queries
CREATE INDEX idx_notification_settings_new_member ON user_notification_settings(new_member_notifications) WHERE new_member_notifications = TRUE;
CREATE INDEX idx_notification_settings_invitations ON user_notification_settings(invitation_notifications) WHERE invitation_notifications = TRUE;