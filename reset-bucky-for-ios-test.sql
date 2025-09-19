-- Reset bucky user for iOS subscription testing
-- This clears the existing Google subscription data so we can test iOS flow

UPDATE app_user 
SET 
    subscription_status = 'trial',
    trial_start_date = CURRENT_TIMESTAMP,
    trial_end_date = CURRENT_TIMESTAMP + INTERVAL '3 days',
    subscription_start_date = NULL,
    subscription_end_date = NULL,
    platform = NULL,
    platform_transaction_id = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'bucky';

-- Also clear payment history for clean testing
DELETE FROM payment_transactions WHERE user_id = (
    SELECT id FROM app_user WHERE username = 'bucky'
);

-- Verify the reset
SELECT username, subscription_status, platform, platform_transaction_id, trial_end_date
FROM app_user WHERE username = 'bucky';

