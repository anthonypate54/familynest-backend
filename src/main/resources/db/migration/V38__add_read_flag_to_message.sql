-- Add read flag to track when user last viewed a message thread
-- This is a simple approach to show comment indicators without complex view tracking

ALTER TABLE message 
ADD COLUMN read_flag TIMESTAMP DEFAULT NULL;

-- Add index for performance when querying by read status
CREATE INDEX IF NOT EXISTS idx_message_read_flag ON message(read_flag);

-- Add comment explaining the purpose
COMMENT ON COLUMN message.read_flag IS 'Timestamp when user last viewed this message thread - used for comment indicators';
