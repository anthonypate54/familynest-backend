-- Remove the unique constraint on platform_transaction_id in payment_transactions table
-- This allows multiple transactions with the same platform_transaction_id but different statuses
ALTER TABLE payment_transactions DROP CONSTRAINT payment_transactions_platform_transaction_id_key;


