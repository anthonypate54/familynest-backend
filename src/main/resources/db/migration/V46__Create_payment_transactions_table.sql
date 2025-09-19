-- Create payment transactions table for detailed payment history
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    platform VARCHAR(10) NOT NULL, -- APPLE, GOOGLE, STRIPE
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT NOT NULL,
    platform_transaction_id VARCHAR(255) UNIQUE,
    product_id VARCHAR(100), -- e.g., premium_monthly, premium_yearly
    status VARCHAR(20) NOT NULL DEFAULT 'completed', -- completed, failed, refunded, pending
    receipt_data TEXT, -- Store Apple/Google receipt data for verification
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient user lookups
CREATE INDEX idx_payment_transactions_user_id ON payment_transactions(user_id);

-- Index for platform transaction ID lookups
CREATE INDEX idx_payment_transactions_platform_tx_id ON payment_transactions(platform_transaction_id);

-- Index for date-based queries
CREATE INDEX idx_payment_transactions_date ON payment_transactions(transaction_date);








