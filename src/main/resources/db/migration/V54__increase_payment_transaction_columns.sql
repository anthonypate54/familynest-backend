-- Increase the length of the status column in payment_transactions table
ALTER TABLE payment_transactions ALTER COLUMN status TYPE VARCHAR(80);

-- Add comment explaining the change
COMMENT ON COLUMN payment_transactions.status IS 'Stores the transaction status from Google Play or Apple App Store (increased from 20 to 80 chars)';

-- Increase the length of the description column in payment_transactions table
ALTER TABLE payment_transactions ALTER COLUMN description TYPE VARCHAR(80);

-- Add comment explaining the change
COMMENT ON COLUMN payment_transactions.description IS 'Stores the transaction description (increased from 20 to 80 chars)';

-- Increase the length of the subscription_state column in payment_transactions table
-- This column was added in V52 with VARCHAR(50) but needs to be longer for Google Play API values
ALTER TABLE payment_transactions ALTER COLUMN subscription_state TYPE VARCHAR(80);

-- Add comment explaining the change
COMMENT ON COLUMN payment_transactions.subscription_state IS 'Current state of the subscription from the platform (e.g., SUBSCRIPTION_STATE_ACTIVE) - increased from 50 to 80 chars';
