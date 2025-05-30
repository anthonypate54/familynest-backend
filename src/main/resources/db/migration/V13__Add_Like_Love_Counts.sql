-- Add like_count and love_count columns to message table
ALTER TABLE message 
ADD COLUMN IF NOT EXISTS like_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS love_count INTEGER DEFAULT 0;

-- Add like_count and love_count columns to message_comment table
ALTER TABLE message_comment 
ADD COLUMN IF NOT EXISTS like_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS love_count INTEGER DEFAULT 0;

-- Create a function to update counts from message_reaction table
CREATE OR REPLACE FUNCTION update_reaction_counts()
RETURNS void AS $$
BEGIN
    -- Update message counts
    UPDATE message m
    SET 
        like_count = COALESCE((
            SELECT COUNT(*) 
            FROM message_reaction mr 
            WHERE mr.message_id = m.id 
            AND mr.reaction_type = 'LIKE'
        ), 0),
        love_count = COALESCE((
            SELECT COUNT(*) 
            FROM message_reaction mr 
            WHERE mr.message_id = m.id 
            AND mr.reaction_type = 'LOVE'
        ), 0);

    -- Update message_comment counts
    UPDATE message_comment mc
    SET 
        like_count = COALESCE((
            SELECT COUNT(*) 
            FROM message_reaction mr 
            WHERE mr.message_id = mc.id 
            AND mr.reaction_type = 'LIKE'
        ), 0),
        love_count = COALESCE((
            SELECT COUNT(*) 
            FROM message_reaction mr 
            WHERE mr.message_id = mc.id 
            AND mr.reaction_type = 'LOVE'
        ), 0);
END;
$$ LANGUAGE plpgsql;

-- Execute the function to populate initial counts
SELECT update_reaction_counts();

-- Drop the temporary function
DROP FUNCTION update_reaction_counts();

-- Add comments to explain the new columns
COMMENT ON COLUMN message.like_count IS 'Number of LIKE reactions on this message';
COMMENT ON COLUMN message.love_count IS 'Number of LOVE reactions on this message';
COMMENT ON COLUMN message_comment.like_count IS 'Number of LIKE reactions on this comment';
COMMENT ON COLUMN message_comment.love_count IS 'Number of LOVE reactions on this comment'; 