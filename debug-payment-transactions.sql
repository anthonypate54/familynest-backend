-- Debug payment transactions for user bucky
-- Check what transactions exist and their dates

-- 1. Find bucky's user ID
SELECT id, username FROM app_user WHERE username = 'bucky';

-- 2. Check all payment transactions for bucky (replace 2 with actual user ID if different)
SELECT id, user_id, transaction_date, amount, description, status, platform, product_id, created_at
FROM payment_transactions 
WHERE user_id = (SELECT id FROM app_user WHERE username = 'bucky')
ORDER BY created_at DESC;

-- 3. Delete all transactions for bucky (clean slate)
DELETE FROM payment_transactions 
WHERE user_id = (SELECT id FROM app_user WHERE username = 'bucky');

-- 4. Verify deletion
SELECT COUNT(*) as transaction_count 
FROM payment_transactions 
WHERE user_id = (SELECT id FROM app_user WHERE username = 'bucky');


