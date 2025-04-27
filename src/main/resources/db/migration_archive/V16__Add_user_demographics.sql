-- This migration is now a no-op since we included these columns in V1__Initial_schema.sql
-- Original SQL:
/*
-- Add demographic columns to user table
ALTER TABLE app_user 
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20),
ADD COLUMN IF NOT EXISTS address VARCHAR(255),
ADD COLUMN IF NOT EXISTS city VARCHAR(100),
ADD COLUMN IF NOT EXISTS state VARCHAR(100),
ADD COLUMN IF NOT EXISTS zip_code VARCHAR(20),
ADD COLUMN IF NOT EXISTS country VARCHAR(100),
ADD COLUMN IF NOT EXISTS birth_date DATE,
ADD COLUMN IF NOT EXISTS bio TEXT,
ADD COLUMN IF NOT EXISTS show_demographics BOOLEAN DEFAULT FALSE;

-- Add indexes for better search performance
CREATE INDEX IF NOT EXISTS idx_app_user_state ON app_user(state);
CREATE INDEX IF NOT EXISTS idx_app_user_country ON app_user(country); 
*/ 