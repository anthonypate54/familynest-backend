-- Reset user bucky to trial state for testing
-- Run this in your PostgreSQL database

-- 1. Reset user subscription to trial
UPDATE app_user 
SET subscription_status = 'trial',
    trial_end_date = NOW() + INTERVAL '30 days',
    trial_start_date = NOW(),
    subscription_start_date = NULL,
    subscription_end_date = NULL,
    platform = NULL,
    platform_transaction_id = NULL,
    updated_at = NOW()
WHERE username = 'bucky';

-- 2. Clear payment transaction history for bucky
DELETE FROM payment_transactions 
WHERE user_id = (SELECT id FROM app_user WHERE username = 'bucky');

-- 3. Verify the reset
SELECT id, username, subscription_status, trial_end_date, platform_transaction_id, platform
FROM app_user 
WHERE username = 'bucky';

-- Expected result: 
-- subscription_status = 'trial'
-- trial_end_date = 30 days from now
-- platform_transaction_id = NULL
-- platform = NULL


