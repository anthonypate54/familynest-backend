-- Add latest_comment_time to message table for comment activity tracking
-- NOTE: This column will be removed in V40 as we simplified to boolean approach

ALTER TABLE message 
ADD COLUMN latest_comment_time TIMESTAMP DEFAULT NULL;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_message_latest_comment_time ON message(latest_comment_time);

-- Add comment explaining the purpose
COMMENT ON COLUMN message.latest_comment_time IS 'Timestamp of most recent comment - will be removed in V40';