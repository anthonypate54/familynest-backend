-- Remove duplicate columns from invitation table
ALTER TABLE invitation 
    DROP COLUMN IF EXISTS inviter_id,
    DROP COLUMN IF EXISTS invitee_email;

-- Add a status column if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'invitation' AND column_name = 'status'
    ) THEN
        ALTER TABLE invitation ADD COLUMN status VARCHAR(50) DEFAULT 'PENDING' NOT NULL;
    END IF;
END $$; 