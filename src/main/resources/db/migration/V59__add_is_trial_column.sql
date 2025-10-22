-- Add is_trial column to app_user table to indicate if subscription is in trial period
-- This is set by Google Play API based on offer details (e.g., 30-dayfreetrial offer)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_trial BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN app_user.is_trial IS 'Indicates if the subscription is currently in its trial period (from Google Play API)';

