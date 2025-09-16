-- Add platform-specific payment fields to app_user table
-- These fields support Apple App Store and Google Play Store integrations

-- Add platform identifier (APPLE, GOOGLE, etc.)
ALTER TABLE app_user ADD COLUMN platform VARCHAR(10);

-- Add platform-specific transaction ID for receipt verification
ALTER TABLE app_user ADD COLUMN platform_transaction_id VARCHAR(255);

-- Create index for efficient platform transaction lookups
CREATE INDEX idx_app_user_platform_transaction ON app_user(platform, platform_transaction_id);

-- Add comment for documentation
COMMENT ON COLUMN app_user.platform IS 'Payment platform: APPLE, GOOGLE, STRIPE, etc.';
COMMENT ON COLUMN app_user.platform_transaction_id IS 'Platform-specific transaction or receipt ID for verification';
