-- Create function to check expired trials
CREATE OR REPLACE FUNCTION check_expired_trials()
RETURNS void AS $$
BEGIN
    -- Update expired trials to 'expired' status
    UPDATE app_user 
    SET subscription_status = 'expired',
        updated_at = CURRENT_TIMESTAMP
    WHERE subscription_status = 'trial' 
    AND trial_end_date < CURRENT_TIMESTAMP;
    
    -- Log the number of expired trials
    RAISE NOTICE 'Expired trials check completed at %', CURRENT_TIMESTAMP;
END;
$$ LANGUAGE plpgsql;

-- Create extension if not exists (may need superuser privileges)
-- CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Schedule the function to run daily at 2 AM
-- This will be enabled manually after ensuring pg_cron is installed
-- SELECT cron.schedule('trial-expiry-check', '0 2 * * *', 'SELECT check_expired_trials();');

-- For now, we'll rely on the Spring Boot scheduler as backup
-- The function is available for manual execution: SELECT check_expired_trials(); 