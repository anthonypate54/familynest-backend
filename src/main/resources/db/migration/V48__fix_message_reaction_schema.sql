-- V48__fix_message_reaction_schema.sql
-- Fix the message_reaction schema to properly handle reactions to both messages and comments

-- Step 1: Add new columns for target_message_id and target_comment_id
ALTER TABLE message_reaction ADD COLUMN target_message_id BIGINT NULL;
ALTER TABLE message_reaction ADD COLUMN target_comment_id BIGINT NULL;

-- Step 2: Migrate data from message_id to the appropriate target column
-- For MESSAGE target_type, copy message_id to target_message_id
UPDATE message_reaction 
SET target_message_id = message_id
WHERE target_type = 'MESSAGE';

-- For COMMENT target_type, copy message_id to target_comment_id
UPDATE message_reaction 
SET target_comment_id = message_id
WHERE target_type = 'COMMENT';

-- Step 3: Add foreign key constraints to the new columns
ALTER TABLE message_reaction 
ADD CONSTRAINT fk_message_reaction_target_message 
FOREIGN KEY (target_message_id) 
REFERENCES message(id) ON DELETE CASCADE;

ALTER TABLE message_reaction 
ADD CONSTRAINT fk_message_reaction_target_comment 
FOREIGN KEY (target_comment_id) 
REFERENCES message_comment(id) ON DELETE CASCADE;

-- Step 4: Update the unique constraint to use the new columns
-- First drop the existing constraint
ALTER TABLE message_reaction 
DROP CONSTRAINT message_reaction_message_id_user_id_reaction_type_target_type_k;

-- Add new unique constraint - can't use COALESCE in constraint definition
-- Instead, we'll create a simpler constraint that works with nulls
ALTER TABLE message_reaction 
ADD CONSTRAINT message_reaction_unique_reaction 
UNIQUE (user_id, reaction_type, target_type, target_message_id, target_comment_id);

-- Step 5: Make message_id nullable (we'll keep it for backward compatibility temporarily)
ALTER TABLE message_reaction 
ALTER COLUMN message_id DROP NOT NULL;

-- Step 6: Drop the old foreign key constraint
ALTER TABLE message_reaction 
DROP CONSTRAINT message_reaction_message_id_fkey;

-- Step 7: Add a check constraint to ensure at least one target ID is set
ALTER TABLE message_reaction 
ADD CONSTRAINT check_message_reaction_target 
CHECK (
    (target_type = 'MESSAGE' AND target_message_id IS NOT NULL AND target_comment_id IS NULL) OR
    (target_type = 'COMMENT' AND target_comment_id IS NOT NULL AND target_message_id IS NULL)
);

-- Step 8: Update the code comment to explain the schema
COMMENT ON TABLE message_reaction IS 'Stores reactions (like, love) to messages and comments with proper foreign key relationships';
