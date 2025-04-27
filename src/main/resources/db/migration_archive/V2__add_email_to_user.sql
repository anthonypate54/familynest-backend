-- This migration is a no-op since email is already included in the V1 schema
-- Original SQL:
/*
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM information_schema.columns 
        WHERE table_name = 'app_user' AND column_name = 'email'
    ) THEN
        ALTER TABLE app_user ADD COLUMN email VARCHAR(255) NOT NULL DEFAULT 'temp@example.com';
    END IF;
END
$$;

-- Add unique constraint if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints
        WHERE table_name = 'app_user' AND constraint_name = 'uk_user_email'
    ) THEN
        ALTER TABLE app_user ADD CONSTRAINT uk_user_email UNIQUE (email);
    END IF;
END
$$;

-- Update existing users with a temporary email if they don't have one
UPDATE app_user SET email = username || '@example.com' WHERE email = 'temp@example.com';
*/ 