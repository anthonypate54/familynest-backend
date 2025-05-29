-- Add parent_message_id column to message table
ALTER TABLE message ADD COLUMN parent_message_id bigint DEFAULT NULL;

-- Add comment to explain the column
COMMENT ON COLUMN message.parent_message_id IS 'References the parent message this comment belongs to. If NULL, this is a regular message.';

-- Add foreign key constraint
ALTER TABLE message ADD CONSTRAINT fk_message_parent 
    FOREIGN KEY (parent_message_id) REFERENCES message(id) ON DELETE CASCADE;

-- Create index for faster lookups
CREATE INDEX idx_message_parent ON message(parent_message_id);