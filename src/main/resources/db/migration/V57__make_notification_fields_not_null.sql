-- Update all columns used for duplicate detection to be NOT NULL
-- notification_type and event_time_millis are new columns from V56
ALTER TABLE payment_transactions ALTER COLUMN notification_type SET NOT NULL;
ALTER TABLE payment_transactions ALTER COLUMN event_time_millis SET NOT NULL;

-- Ensure platform_transaction_id and platform are also NOT NULL
-- These should already be NOT NULL, but let's make sure
ALTER TABLE payment_transactions ALTER COLUMN platform_transaction_id SET NOT NULL;
ALTER TABLE payment_transactions ALTER COLUMN platform SET NOT NULL;
