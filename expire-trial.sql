-- Temporarily expire trial to test purchase flow
-- Run this in your PostgreSQL to force subscription required state

UPDATE app_user 
SET subscription_status = 'expired',
    trial_end_date = NOW() - INTERVAL '1 day',
    updated_at = NOW()
WHERE username = 'bucky';  -- Replace with your test username

-- To restore trial later:
-- UPDATE app_user 
-- SET subscription_status = 'trial',
--     trial_end_date = NOW() + INTERVAL '30 days',
--     updated_at = NOW()
-- WHERE username = 'bucky';


