-- Create a dedicated table for tracking processed RTDN notifications
CREATE TABLE rtdn_processed_notifications (
    id SERIAL PRIMARY KEY,
    platform VARCHAR(10) NOT NULL,  -- GOOGLE or APPLE
    purchase_token VARCHAR(255) NOT NULL,
    notification_type INTEGER NOT NULL,
    event_time_millis BIGINT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_notification UNIQUE (platform, purchase_token, notification_type, event_time_millis)
);

-- Add comment explaining the purpose of the table
COMMENT ON TABLE rtdn_processed_notifications IS 'Tracks which Real-Time Developer Notifications have been processed to prevent duplicate processing';

-- Drop the unique constraint on payment_transactions that was used for duplicate detection
DROP INDEX IF EXISTS idx_payment_transactions_notification_unique;

-- Remove notification-specific fields from payment_transactions
ALTER TABLE payment_transactions DROP COLUMN IF EXISTS notification_type;
ALTER TABLE payment_transactions DROP COLUMN IF EXISTS event_time_millis;
