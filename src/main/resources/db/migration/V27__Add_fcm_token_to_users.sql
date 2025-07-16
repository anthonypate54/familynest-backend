-- V27: Add FCM token storage for push notifications
-- This column stores Firebase Cloud Messaging tokens for sending push notifications

-- Add FCM token column to app_user table
ALTER TABLE app_user 
ADD COLUMN fcm_token VARCHAR(255);

-- Create index for efficient FCM token lookups
CREATE INDEX idx_app_user_fcm_token ON app_user(fcm_token);

-- Add comment for clarity
COMMENT ON COLUMN app_user.fcm_token 
IS 'Firebase Cloud Messaging token for sending push notifications to user devices';

-- Note: FCM tokens are initially NULL and will be updated when users log in and register their device tokens 