-- Add simple boolean flag to track unread comments
-- Much simpler than timestamp comparisons

ALTER TABLE message 
ADD COLUMN has_unread_comments BOOLEAN DEFAULT FALSE;

-- Add index for performance when querying by read status
CREATE INDEX IF NOT EXISTS idx_message_has_unread_comments ON message(has_unread_comments);

-- Add comment explaining the purpose
COMMENT ON COLUMN message.has_unread_comments IS 'True when there are unread comments on this message thread';
