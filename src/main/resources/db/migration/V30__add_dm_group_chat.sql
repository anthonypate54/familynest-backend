-- Add group chat support to DM system
-- This migration adds the ability to create group chats with multiple participants

-- Add group chat columns to existing dm_conversation table
ALTER TABLE dm_conversation 
ADD COLUMN name VARCHAR(100) DEFAULT NULL,              -- Group name (NULL for 1:1 chats)
ADD COLUMN is_group BOOLEAN DEFAULT FALSE NOT NULL,     -- FALSE for 1:1, TRUE for groups
ADD COLUMN created_by_user_id BIGINT DEFAULT NULL;      -- Group creator

-- Add foreign key constraint for group creator
ALTER TABLE dm_conversation 
ADD CONSTRAINT fk_dm_conv_creator 
FOREIGN KEY (created_by_user_id) REFERENCES app_user(id);

-- Create dm_conversation_participant table for group memberships
CREATE TABLE dm_conversation_participant (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  -- Foreign key constraints
  CONSTRAINT fk_dm_participant_conv 
    FOREIGN KEY (conversation_id) REFERENCES dm_conversation(id) ON DELETE CASCADE,
  CONSTRAINT fk_dm_participant_user 
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    
  -- Unique constraint to prevent duplicate memberships
  CONSTRAINT unique_conversation_participant 
    UNIQUE (conversation_id, user_id)
);

-- Create indexes for performance
CREATE INDEX idx_dm_participant_conversation ON dm_conversation_participant(conversation_id);
CREATE INDEX idx_dm_participant_user ON dm_conversation_participant(user_id);
CREATE INDEX idx_dm_conversation_group ON dm_conversation(is_group);
CREATE INDEX idx_dm_conversation_creator ON dm_conversation(created_by_user_id);

-- Migrate existing 1:1 conversations to new structure
-- For existing conversations, create participant records for user1_id and user2_id
INSERT INTO dm_conversation_participant (conversation_id, user_id, joined_at)
SELECT 
  id as conversation_id,
  user1_id as user_id,
  created_at as joined_at
FROM dm_conversation 
WHERE user1_id IS NOT NULL;

INSERT INTO dm_conversation_participant (conversation_id, user_id, joined_at)
SELECT 
  id as conversation_id,
  user2_id as user_id,
  created_at as joined_at
FROM dm_conversation 
WHERE user2_id IS NOT NULL AND user1_id != user2_id;

-- Update existing conversations to mark them as non-group (1:1) conversations
UPDATE dm_conversation SET is_group = FALSE WHERE is_group IS NULL;

-- Add comment for documentation
COMMENT ON TABLE dm_conversation_participant IS 'Stores participants for group DM conversations. For 1:1 chats, contains exactly 2 participants.';
COMMENT ON COLUMN dm_conversation.name IS 'Group name for group chats, NULL for 1:1 conversations';
COMMENT ON COLUMN dm_conversation.is_group IS 'TRUE for group chats, FALSE for 1:1 conversations';
COMMENT ON COLUMN dm_conversation.created_by_user_id IS 'User who created the group chat, NULL for 1:1 conversations'; 