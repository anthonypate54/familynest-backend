-- Create a new table to store subscription notifications
CREATE TABLE subscription_notifications (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    purchase_token VARCHAR(255) NOT NULL,
    notification_type INTEGER NOT NULL,
    event_time_millis BIGINT NOT NULL,
    product_id VARCHAR(255),
    processed BOOLEAN DEFAULT TRUE,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    raw_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add comment explaining the purpose of the table
COMMENT ON TABLE subscription_notifications IS 'Stores subscription notifications from Google Play and Apple App Store for idempotency and audit purposes';

-- Create indexes for efficient lookups
CREATE INDEX idx_subscription_notifications_purchase_token ON subscription_notifications(purchase_token);

-- Create a unique index for duplicate detection
CREATE UNIQUE INDEX idx_subscription_notifications_unique ON subscription_notifications(platform, purchase_token, notification_type, event_time_millis);

-- Add foreign key to payment_transactions
ALTER TABLE payment_transactions ADD COLUMN notification_id BIGINT;
ALTER TABLE payment_transactions ADD CONSTRAINT fk_payment_transactions_notification FOREIGN KEY (notification_id) REFERENCES subscription_notifications(id);

-- Add comment explaining the purpose of the column
COMMENT ON COLUMN payment_transactions.notification_id IS 'Reference to the subscription notification that triggered this transaction';
