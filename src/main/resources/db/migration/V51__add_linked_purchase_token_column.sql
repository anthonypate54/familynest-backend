-- Add linked_purchase_token column to payment_transactions table
ALTER TABLE payment_transactions ADD COLUMN linked_purchase_token VARCHAR(255);

-- Create index for faster lookups by linked_purchase_token
CREATE INDEX idx_payment_transactions_linked_purchase_token ON payment_transactions(linked_purchase_token);

-- Add comment explaining the purpose of the column
COMMENT ON COLUMN payment_transactions.linked_purchase_token IS 'Stores the linkedPurchaseToken from Google Play API to handle subscription linking';
