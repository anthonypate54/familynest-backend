-- Add notification-specific fields to payment_transactions for duplicate detection
ALTER TABLE payment_transactions ADD COLUMN notification_type INTEGER;
ALTER TABLE payment_transactions ADD COLUMN event_time_millis BIGINT;

-- Add comments explaining the purpose of the columns
COMMENT ON COLUMN payment_transactions.notification_type IS 'The notification type from Google Play (e.g., 4=SUBSCRIPTION_PURCHASED, 2=SUBSCRIPTION_RENEWED)';
COMMENT ON COLUMN payment_transactions.event_time_millis IS 'The eventTimeMillis from Google Play notifications for duplicate detection';

-- Create a unique index for duplicate detection
CREATE UNIQUE INDEX idx_payment_transactions_notification_unique 
ON payment_transactions(platform_transaction_id, platform, notification_type, event_time_millis);
