-- Add target_type column to message_reaction
ALTER TABLE message_reaction 
ADD COLUMN target_type VARCHAR(10) NOT NULL DEFAULT 'MESSAGE' 
CHECK (target_type IN ('MESSAGE', 'COMMENT'));

-- Update existing reactions to be of type MESSAGE (since they were all for messages)
UPDATE message_reaction SET target_type = 'MESSAGE';

-- Drop old unique constraint and add new one that includes target_type
ALTER TABLE message_reaction 
DROP CONSTRAINT message_reaction_message_id_user_id_reaction_type_key,
ADD CONSTRAINT message_reaction_message_id_user_id_reaction_type_target_type_key 
UNIQUE (message_id, user_id, reaction_type, target_type); 