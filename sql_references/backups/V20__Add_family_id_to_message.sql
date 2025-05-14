-- Add family_id column to message table if it doesn't exist
ALTER TABLE message ADD COLUMN IF NOT EXISTS family_id BIGINT;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_message_family ON message(family_id);

-- Add foreign key constraint 
ALTER TABLE message
ADD CONSTRAINT fk_message_family FOREIGN KEY (family_id) REFERENCES family(id);

COMMENT ON COLUMN message.family_id IS 'The family this message belongs to'; 