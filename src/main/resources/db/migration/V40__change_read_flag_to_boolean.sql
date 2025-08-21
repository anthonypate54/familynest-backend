-- Change read_flag from TIMESTAMP to BOOLEAN for simple unread indicator
-- Also remove latest_comment_time column that we don't need
-- Simple logic: false = has unread comments (show red), true = all comments read (normal icon)

-- Drop the old index
DROP INDEX IF EXISTS idx_message_read_flag;

-- Remove latest_comment_time column if it exists (from V39)
ALTER TABLE message DROP COLUMN IF EXISTS latest_comment_time;

-- Change the column type from TIMESTAMP to BOOLEAN  
ALTER TABLE message 
ALTER COLUMN read_flag TYPE BOOLEAN USING (read_flag IS NOT NULL);

-- Set default value
ALTER TABLE message 
ALTER COLUMN read_flag SET DEFAULT false;

-- Add new index for performance
CREATE INDEX IF NOT EXISTS idx_message_read_flag ON message(read_flag);

-- Update comment
COMMENT ON COLUMN message.read_flag IS 'Boolean flag: false = has unread comments (show red icon), true = all comments read';
