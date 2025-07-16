-- V24: Change notification defaults to FALSE (opt-in approach)
-- Users must explicitly choose to enable notifications

-- Update table defaults by altering column constraints
-- DM Notification Settings
ALTER TABLE user_dm_notification_settings 
ALTER COLUMN receive_dm_notifications SET DEFAULT FALSE,
ALTER COLUMN email_dm_notifications SET DEFAULT FALSE,
ALTER COLUMN push_dm_notifications SET DEFAULT FALSE;

-- Global Notification Settings  
ALTER TABLE user_global_notification_settings
ALTER COLUMN email_notifications_enabled SET DEFAULT FALSE,
ALTER COLUMN push_notifications_enabled SET DEFAULT FALSE,
ALTER COLUMN weekend_notifications SET DEFAULT FALSE;

-- Invitation Notification Settings
ALTER TABLE user_invitation_notification_settings
ALTER COLUMN receive_invitation_notifications SET DEFAULT FALSE,
ALTER COLUMN email_invitation_notifications SET DEFAULT FALSE, 
ALTER COLUMN push_invitation_notifications SET DEFAULT FALSE,
ALTER COLUMN notify_on_invitation_accepted SET DEFAULT FALSE;

-- Update existing user records to FALSE (opt-in approach)
-- Only update records that still have the default TRUE values

-- Update DM notification settings
UPDATE user_dm_notification_settings 
SET receive_dm_notifications = FALSE,
    email_dm_notifications = FALSE,
    push_dm_notifications = FALSE,
    last_updated = CURRENT_TIMESTAMP
WHERE receive_dm_notifications = TRUE 
  AND email_dm_notifications = TRUE 
  AND push_dm_notifications = TRUE;

-- Update global notification settings
UPDATE user_global_notification_settings
SET email_notifications_enabled = FALSE,
    push_notifications_enabled = FALSE, 
    weekend_notifications = FALSE,
    last_updated = CURRENT_TIMESTAMP
WHERE email_notifications_enabled = TRUE
  AND push_notifications_enabled = TRUE
  AND weekend_notifications = TRUE;

-- Update invitation notification settings  
UPDATE user_invitation_notification_settings
SET receive_invitation_notifications = FALSE,
    email_invitation_notifications = FALSE,
    push_invitation_notifications = FALSE,
    notify_on_invitation_accepted = FALSE,
    last_updated = CURRENT_TIMESTAMP
WHERE receive_invitation_notifications = TRUE
  AND email_invitation_notifications = TRUE
  AND push_invitation_notifications = TRUE
  AND notify_on_invitation_accepted = TRUE;

-- Leave notify_on_invitation_declined as FALSE (was already FALSE)
-- Leave quiet_hours_enabled as FALSE (was already FALSE) 