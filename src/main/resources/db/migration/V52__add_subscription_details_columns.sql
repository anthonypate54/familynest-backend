-- Add new columns to payment_transactions table to store platform-agnostic subscription details

-- Add currency column
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(10);
COMMENT ON COLUMN payment_transactions.currency IS 'Currency code (e.g., USD, EUR) for the transaction';

-- Add is_trial column
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS is_trial BOOLEAN;
COMMENT ON COLUMN payment_transactions.is_trial IS 'Whether this transaction is for a free trial';

-- Add offer_id column
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS offer_id VARCHAR(100);
COMMENT ON COLUMN payment_transactions.offer_id IS 'Offer ID from the platform (e.g., 30-dayfreetrial for Google Play)';

-- Add subscription_state column
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS subscription_state VARCHAR(50);
COMMENT ON COLUMN payment_transactions.subscription_state IS 'Current state of the subscription from the platform (e.g., ACTIVE, CANCELED)';

-- Add start_time column
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS start_time VARCHAR(50);
COMMENT ON COLUMN payment_transactions.start_time IS 'Start time of the subscription from the platform';

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_payment_transactions_is_trial ON payment_transactions(is_trial);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_offer_id ON payment_transactions(offer_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_subscription_state ON payment_transactions(subscription_state);


