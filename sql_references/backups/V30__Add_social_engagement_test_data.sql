-- Migration: V30__Add_social_engagement_test_data.sql
-- Description: Adds test data for social engagement features including
-- reactions, comments, views, and shares for testing purposes

-- Get some message IDs from the database
DO $$ 
DECLARE
    message_id1 INTEGER;
    message_id2 INTEGER;
    message_id3 INTEGER;
    user_id1 INTEGER;
    user_id2 INTEGER;
    user_id3 INTEGER;
    user_id4 INTEGER;
    user_id5 INTEGER;
    family_id1 INTEGER;
    family_id2 INTEGER;
BEGIN
    -- Get some existing messages (if any)
    SELECT id INTO message_id1 FROM message ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id INTO message_id2 FROM message ORDER BY id LIMIT 1 OFFSET 1;
    SELECT id INTO message_id3 FROM message ORDER BY id LIMIT 1 OFFSET 2;
    
    -- Get some existing users
    SELECT id INTO user_id1 FROM app_user ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id INTO user_id2 FROM app_user ORDER BY id LIMIT 1 OFFSET 1;
    SELECT id INTO user_id3 FROM app_user ORDER BY id LIMIT 1 OFFSET 2;
    SELECT id INTO user_id4 FROM app_user ORDER BY id LIMIT 1 OFFSET 3;
    SELECT id INTO user_id5 FROM app_user ORDER BY id LIMIT 1 OFFSET 4;
    
    -- Get some existing families
    SELECT id INTO family_id1 FROM family ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id INTO family_id2 FROM family ORDER BY id LIMIT 1 OFFSET 1;
    
    -- Only proceed if we have data to work with AND user_id1 is not null
    IF message_id1 IS NOT NULL AND user_id1 IS NOT NULL AND family_id1 IS NOT NULL THEN
        -- Add reactions to message 1 (checking each user_id is not null before using it)
        IF user_id1 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id1, user_id1, 'LIKE', NOW() - INTERVAL '3 hours');
        END IF;
        
        IF user_id2 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id1, user_id2, 'LIKE', NOW() - INTERVAL '2 hours 30 minutes');
        END IF;
        
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id1, user_id3, 'LOVE', NOW() - INTERVAL '2 hours');
        END IF;
        
        IF user_id4 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id1, user_id4, 'LIKE', NOW() - INTERVAL '1 hour 30 minutes');
        END IF;
        
        IF user_id5 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id1, user_id5, 'LAUGH', NOW() - INTERVAL '1 hour');
        END IF;
        
        -- Add comments to message 1 (with null checks)
        IF user_id2 IS NOT NULL THEN
            INSERT INTO message_comment (message_id, user_id, content, created_at)
            VALUES (message_id1, user_id2, 'Great photo!', NOW() - INTERVAL '2 hours 15 minutes');
        END IF;
        
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_comment (message_id, user_id, content, created_at)
            VALUES (message_id1, user_id3, 'Thanks for sharing this!', NOW() - INTERVAL '1 hour 45 minutes');
        END IF;
        
        -- Add a reply to the first comment (if user_id1 is not null)
        IF user_id1 IS NOT NULL THEN
            -- Get the first comment ID
            DECLARE
                first_comment_id INTEGER;
            BEGIN
                SELECT id INTO first_comment_id FROM message_comment 
                WHERE message_id = message_id1 
                ORDER BY id LIMIT 1;
                
                IF first_comment_id IS NOT NULL THEN
                    INSERT INTO message_comment (message_id, user_id, content, parent_comment_id, created_at)
                    VALUES (message_id1, user_id1, 'Thank you!', first_comment_id, NOW() - INTERVAL '1 hour 30 minutes');
                END IF;
            END;
        END IF;
        
        -- Add views to message 1 (with null checks)
        IF user_id1 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id1, user_id1, NOW() - INTERVAL '3 hours 10 minutes');
        END IF;
        
        IF user_id2 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id1, user_id2, NOW() - INTERVAL '2 hours 40 minutes');
        END IF;
        
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id1, user_id3, NOW() - INTERVAL '2 hours 5 minutes');
        END IF;
        
        IF user_id4 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id1, user_id4, NOW() - INTERVAL '1 hour 35 minutes');
        END IF;
        
        IF user_id5 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id1, user_id5, NOW() - INTERVAL '1 hour 5 minutes');
        END IF;
        
        -- Add shares for message 1 (with null checks)
        IF user_id2 IS NOT NULL AND family_id2 IS NOT NULL THEN
            INSERT INTO message_share (original_message_id, shared_by_user_id, shared_to_family_id, shared_at)
            VALUES (message_id1, user_id2, family_id2, NOW() - INTERVAL '2 hours');
        END IF;
    END IF;
    
    -- Add data for message 2 if it exists (with null checks)
    IF message_id2 IS NOT NULL AND user_id1 IS NOT NULL AND family_id1 IS NOT NULL THEN
        -- Add reactions to message 2
        IF user_id2 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id2, user_id2, 'LIKE', NOW() - INTERVAL '1 day 3 hours');
        END IF;
        
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id2, user_id3, 'LIKE', NOW() - INTERVAL '1 day 2 hours');
        END IF;
        
        IF user_id4 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id2, user_id4, 'LOVE', NOW() - INTERVAL '1 day 1 hour');
        END IF;
        
        -- Add views to message 2
        IF user_id2 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id2, user_id2, NOW() - INTERVAL '1 day 3 hours 5 minutes');
        END IF;
        
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id2, user_id3, NOW() - INTERVAL '1 day 2 hours 5 minutes');
        END IF;
        
        IF user_id4 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id2, user_id4, NOW() - INTERVAL '1 day 1 hour 5 minutes');
        END IF;
        
        IF user_id5 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id2, user_id5, NOW() - INTERVAL '1 day 30 minutes');
        END IF;
    END IF;
    
    -- Add data for message 3 if it exists (with null checks)
    IF message_id3 IS NOT NULL AND user_id1 IS NOT NULL AND family_id1 IS NOT NULL THEN
        -- Add a reaction to message 3
        IF user_id5 IS NOT NULL THEN
            INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
            VALUES (message_id3, user_id5, 'LIKE', NOW() - INTERVAL '5 hours');
            
            -- Add a comment to message 3
            INSERT INTO message_comment (message_id, user_id, content, created_at)
            VALUES (message_id3, user_id5, 'Interesting!', NOW() - INTERVAL '4 hours 50 minutes');
        END IF;
        
        -- Add views to message 3
        IF user_id3 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id3, user_id3, NOW() - INTERVAL '5 hours 10 minutes');
        END IF;
        
        IF user_id5 IS NOT NULL THEN
            INSERT INTO message_view (message_id, user_id, viewed_at)
            VALUES (message_id3, user_id5, NOW() - INTERVAL '4 hours 55 minutes');
        END IF;
    END IF;
END $$; 