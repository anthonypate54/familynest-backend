-- Add support for DM message tracking in message_view table
-- This allows us to track read status for both family messages and DM messages

-- Add new columns to message_view table
ALTER TABLE message_view ADD COLUMN dm_message_id BIGINT DEFAULT NULL;
ALTER TABLE message_view ADD COLUMN message_type VARCHAR(20) DEFAULT 'family' NOT NULL;

-- Update existing records to have explicit message_type
UPDATE message_view SET message_type = 'family' WHERE message_id IS NOT NULL;

-- Add foreign key constraint for dm_message_id
ALTER TABLE message_view ADD CONSTRAINT fk_message_view_dm_message 
    FOREIGN KEY (dm_message_id) REFERENCES dm_message(id) ON DELETE CASCADE;

-- Create index for dm_message_id lookups
CREATE INDEX idx_message_view_dm_message ON message_view(dm_message_id);
CREATE INDEX idx_message_view_type ON message_view(message_type);

-- Update unique constraint to include message_type
-- First drop the old constraint
ALTER TABLE message_view DROP CONSTRAINT message_view_message_id_user_id_key;

-- Add new compound unique constraint
-- For family messages: (message_id, user_id, message_type) must be unique
-- For DM messages: (dm_message_id, user_id, message_type) must be unique
ALTER TABLE message_view ADD CONSTRAINT message_view_unique_per_type 
    UNIQUE (message_id, dm_message_id, user_id, message_type);

-- Add check constraint to ensure exactly one message ID is set
ALTER TABLE message_view ADD CONSTRAINT message_view_one_message_id_check 
    CHECK (
        (message_id IS NOT NULL AND dm_message_id IS NULL AND message_type = 'family') OR
        (message_id IS NULL AND dm_message_id IS NOT NULL AND message_type = 'dm')
    );

-- Add comments for documentation
COMMENT ON COLUMN message_view.dm_message_id IS 'References dm_message.id when tracking DM message views';
COMMENT ON COLUMN message_view.message_type IS 'Type of message: family, dm, or group_dm (future)';
COMMENT ON CONSTRAINT message_view_one_message_id_check ON message_view IS 'Ensures exactly one of message_id or dm_message_id is set based on message_type'; 