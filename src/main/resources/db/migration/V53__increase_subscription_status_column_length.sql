-- Increase the length of the subscription_status column in app_user table
ALTER TABLE app_user ALTER COLUMN subscription_status TYPE VARCHAR(80);

-- Add comment explaining the change
COMMENT ON COLUMN app_user.subscription_status IS 'Stores the subscription status from Google Play or Apple App Store (increased from 20 to 80 chars)';
