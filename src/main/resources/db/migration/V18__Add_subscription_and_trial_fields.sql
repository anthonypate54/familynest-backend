-- Add subscription and trial fields to app_user table
ALTER TABLE app_user ADD COLUMN subscription_status VARCHAR(20) DEFAULT 'trial';
ALTER TABLE app_user ADD COLUMN trial_start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_user ADD COLUMN trial_end_date TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '30 days');
ALTER TABLE app_user ADD COLUMN subscription_start_date TIMESTAMP;
ALTER TABLE app_user ADD COLUMN subscription_end_date TIMESTAMP;
ALTER TABLE app_user ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE app_user ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE app_user ADD COLUMN stripe_subscription_id VARCHAR(255);
ALTER TABLE app_user ADD COLUMN monthly_price DECIMAL(10,2) DEFAULT 4.99;
ALTER TABLE app_user ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_user ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Create index for efficient trial expiry checks
CREATE INDEX idx_app_user_trial_status ON app_user(subscription_status, trial_end_date);

-- Update existing users to have trial status with 30-day trial from now
UPDATE app_user SET 
    subscription_status = 'trial',
    trial_start_date = CURRENT_TIMESTAMP,
    trial_end_date = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE subscription_status IS NULL; 