-- Add message_family_link table for many-to-many relationship between messages and families
-- This allows a single message to be visible to multiple families
-- We keep the existing family_id column in message table during transition

CREATE TABLE message_family_link (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,  
    family_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_msg_family_link_message FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_family_link_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    
    -- Ensure no duplicate message-family combinations
    CONSTRAINT unique_message_family UNIQUE (message_id, family_id)
);

-- Indexes for performance
CREATE INDEX idx_message_family_link_message ON message_family_link(message_id);
CREATE INDEX idx_message_family_link_family ON message_family_link(family_id);
CREATE INDEX idx_message_family_link_created ON message_family_link(created_at);

-- Comments for documentation
COMMENT ON TABLE message_family_link IS 'Many-to-many relationship between messages and families - allows a single message to be visible to multiple families';
COMMENT ON COLUMN message_family_link.message_id IS 'ID of the message';
COMMENT ON COLUMN message_family_link.family_id IS 'ID of the family that can see this message';
COMMENT ON COLUMN message_family_link.created_at IS 'When this message-family link was created';

-- Optional: Populate the link table with existing message data for backward compatibility
-- This allows existing messages to work with both old and new query patterns
INSERT INTO message_family_link (message_id, family_id, created_at)
SELECT id, family_id, COALESCE(timestamp, CURRENT_TIMESTAMP)
FROM message 
WHERE family_id IS NOT NULL; 