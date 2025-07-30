-- Fix dm_conversation table to allow null user1_id and user2_id for group chats
-- These columns are only relevant for 1:1 conversations, not groups

-- Make user1_id and user2_id nullable for group chat support
ALTER TABLE dm_conversation 
ALTER COLUMN user1_id DROP NOT NULL,
ALTER COLUMN user2_id DROP NOT NULL;

-- Add comments for clarity
COMMENT ON COLUMN dm_conversation.user1_id IS 'Lower user ID for 1:1 conversations, NULL for group chats';
COMMENT ON COLUMN dm_conversation.user2_id IS 'Higher user ID for 1:1 conversations, NULL for group chats'; 