-- This migration is now a no-op since sender_username column doesn't exist yet at this point
-- It will be added in V13 and populated there.
-- Original SQL:
/*
-- Update sender_ids for all messages based on sender_username
UPDATE message m
SET sender_id = (
    SELECT id 
    FROM app_user u 
    WHERE u.username = m.sender_username
)
WHERE m.sender_id IS NULL
AND m.sender_username IS NOT NULL;
*/ 