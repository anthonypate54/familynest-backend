-- Test Messages Dataset for FamilyNest
-- This script creates realistic message data for testing the messaging functionality
-- It generates 50 message threads with approximately 200 total messages

-- Clear existing message data to avoid duplication
DELETE FROM message_comment;
DELETE FROM message_reaction;
DELETE FROM message_view;
DELETE FROM message_share;
DELETE FROM message;

-- Create sequence for message IDs starting at 10000
DO $$
BEGIN
    -- Drop sequence if it exists
    DROP SEQUENCE IF EXISTS message_id_seq;
    
    -- Create sequence
    CREATE SEQUENCE message_id_seq START 10000;
END $$;

-- Function to generate random timestamps within a date range
CREATE OR REPLACE FUNCTION random_timestamp(start_date timestamp, end_date timestamp) 
RETURNS timestamp AS $$
BEGIN
    RETURN start_date + random() * (end_date - start_date);
END;
$$ LANGUAGE plpgsql;

-- Generate messages for each family
DO $$
DECLARE
    family_rec RECORD;
    user_rec RECORD;
    message_id BIGINT;
    message_count INT;
    thread_count INT := 0;
    total_messages INT := 0;
    current_timestamp TIMESTAMP;
    thread_start_timestamp TIMESTAMP;
    message_content TEXT;
    media_type TEXT;
    media_url TEXT;
    sender_username TEXT;
    user_cursor REFCURSOR;
    reply_user_rec RECORD;
    last_message_id BIGINT;
    thread_family_id BIGINT;
    thread_starter_id BIGINT;
    has_media BOOLEAN;
    media_messages TEXT[] := ARRAY[
        'Check out this photo I took yesterday!',
        'Here''s the video from our gathering',
        'I thought you might like this image',
        'I finally managed to capture this moment on camera',
        'A picture is worth a thousand words',
        'Look what I found in our old albums',
        'This deserves to be shared with the family',
        'I couldn''t believe my eyes when I saw this',
        'This brings back so many memories',
        'Just had to share this special moment'
    ];
    text_messages TEXT[] := ARRAY[
        'How is everyone doing today?',
        'Just wanted to check in and see how you''re all doing',
        'I miss you all so much!',
        'When are we planning our next family get-together?',
        'Happy birthday to the best cousin in the world!',
        'Congratulations on your new job!',
        'I''m so proud of what you''ve accomplished',
        'Remember when we used to spend summers at grandma''s house?',
        'Does anyone have plans for the holidays yet?',
        'I found an old recipe that mom used to make',
        'Who''s coming to the reunion next month?',
        'I''m thinking about visiting next weekend, is that good for everyone?',
        'We should plan a video call soon to catch up',
        'Has anyone heard from uncle Bob lately?',
        'I''m moving to a new apartment next week',
        'Just got some great news I wanted to share with the family',
        'Thank you all for your support during this difficult time',
        'I can''t believe it''s been 5 years since our last reunion',
        'Would love to hear what everyone has been up to',
        'Just a reminder about the annual family picnic next Saturday',
        'Look forward to seeing everyone soon!',
        'Anyone have recommendations for a good family vacation spot?',
        'Should we start a new family tradition this year?',
        'I found some old family photos while cleaning out the attic',
        'Who''s bringing what to the potluck?',
        'Does anyone need a ride to the airport?',
        'I''m so grateful for this family',
        'Let''s all wish Sarah good luck on her exam tomorrow!',
        'Just wanted to say I love you all',
        'Remember to RSVP for the wedding by next Friday'
    ];
    reply_messages TEXT[] := ARRAY[
        'That''s wonderful news!',
        'I''m so happy to hear that',
        'Thanks for sharing this with us',
        'I couldn''t agree more',
        'When did this happen?',
        'I was just thinking about this the other day',
        'Count me in!',
        'That brings back so many memories',
        'I''ll definitely be there',
        'Sorry I can''t make it, but have fun!',
        'Has anyone else heard about this?',
        'Can''t wait to see everyone',
        'I''m looking forward to it',
        'That''s exactly what I needed to hear today',
        'I miss you all too!',
        'Let me check my calendar and get back to you',
        'That''s a great idea',
        'I think we should definitely do that',
        'How about next weekend instead?',
        'I completely forgot about that!',
        'That sounds perfect',
        'I''ll help organize if needed',
        'Keep us posted on how it goes',
        'I was just about to suggest the same thing',
        'Who else is planning to attend?',
        'Do we need to bring anything?',
        'I remember that so well!',
        'Those were the good old days',
        'This made my day',
        'Love seeing updates from the family'
    ];
BEGIN
    -- Loop through each family
    FOR family_rec IN (SELECT id FROM family ORDER BY id LIMIT 10) LOOP
        -- Decide how many thread starters for this family (2-7)
        thread_count := floor(random() * 5) + 2;
        
        -- Get users from this family to be message senders
        FOR i IN 1..thread_count LOOP
            -- Get a random user from this family
            OPEN user_cursor FOR 
                SELECT u.id, u.username, fm.family_id
                FROM app_user u
                JOIN user_family_membership fm ON u.id = fm.user_id
                WHERE fm.family_id = family_rec.id
                ORDER BY random()
                LIMIT 1;
            
            FETCH user_cursor INTO user_rec;
            CLOSE user_cursor;
            
            -- Skip if no user found (shouldn't happen with our test data)
            CONTINUE WHEN user_rec IS NULL;
            
            -- Get next message ID
            SELECT nextval('message_id_seq') INTO message_id;
            
            -- Create a thread starter message
            thread_start_timestamp := random_timestamp('2025-01-01 00:00:00'::timestamp, '2025-05-01 00:00:00'::timestamp);
            has_media := random() < 0.3; -- 30% chance of having media
            
            IF has_media THEN
                message_content := media_messages[floor(random() * array_length(media_messages, 1)) + 1];
                media_type := CASE 
                                WHEN random() < 0.6 THEN 'image'
                                WHEN random() < 0.9 THEN 'video' 
                                ELSE 'audio'
                              END;
                media_url := '/uploads/sample_' || 
                             CASE media_type 
                                WHEN 'image' THEN 'img_' || floor(random() * 10) || '.jpg'
                                WHEN 'video' THEN 'vid_' || floor(random() * 5) || '.mp4'
                                ELSE 'audio_' || floor(random() * 3) || '.mp3'
                             END;
            ELSE
                message_content := text_messages[floor(random() * array_length(text_messages, 1)) + 1];
                media_type := NULL;
                media_url := NULL;
            END IF;
            
            -- Insert thread starter message
            INSERT INTO message (id, content, family_id, media_type, media_url, sender_id, sender_username, timestamp, user_id)
            VALUES (
                message_id,
                message_content,
                family_rec.id,
                media_type,
                media_url,
                user_rec.id,
                user_rec.username,
                thread_start_timestamp,
                user_rec.id
            );
            
            total_messages := total_messages + 1;
            last_message_id := message_id;
            thread_family_id := family_rec.id;
            thread_starter_id := user_rec.id;
            
            -- Add 1-5 replies to this thread
            message_count := floor(random() * 5) + 1;
            
            -- Add replies
            FOR j IN 1..message_count LOOP
                -- Get another random user from the same family (different from thread starter if possible)
                OPEN user_cursor FOR 
                    SELECT u.id, u.username
                    FROM app_user u
                    JOIN user_family_membership fm ON u.id = fm.user_id
                    WHERE fm.family_id = thread_family_id
                    AND u.id != thread_starter_id
                    ORDER BY random()
                    LIMIT 1;
                
                FETCH user_cursor INTO reply_user_rec;
                
                -- If no other user, use thread starter (fallback)
                IF NOT FOUND THEN
                    reply_user_rec := user_rec;
                END IF;
                
                CLOSE user_cursor;
                
                -- Get next message ID
                SELECT nextval('message_id_seq') INTO message_id;
                
                -- Create a reply message (1-48 hours after previous message)
                current_timestamp := thread_start_timestamp + (random() * 48 * interval '1 hour');
                
                -- 10% chance of having media in replies
                has_media := random() < 0.1;
                
                IF has_media THEN
                    message_content := media_messages[floor(random() * array_length(media_messages, 1)) + 1];
                    media_type := CASE 
                                    WHEN random() < 0.7 THEN 'image'
                                    WHEN random() < 0.9 THEN 'video' 
                                    ELSE 'audio'
                                  END;
                    media_url := '/uploads/sample_' || 
                                 CASE media_type 
                                    WHEN 'image' THEN 'img_' || floor(random() * 10) || '.jpg'
                                    WHEN 'video' THEN 'vid_' || floor(random() * 5) || '.mp4'
                                    ELSE 'audio_' || floor(random() * 3) || '.mp3'
                                 END;
                ELSE
                    message_content := reply_messages[floor(random() * array_length(reply_messages, 1)) + 1];
                    media_type := NULL;
                    media_url := NULL;
                END IF;
                
                -- Insert reply message
                INSERT INTO message (id, content, family_id, media_type, media_url, sender_id, sender_username, timestamp, user_id)
                VALUES (
                    message_id,
                    message_content,
                    thread_family_id,
                    media_type,
                    media_url,
                    reply_user_rec.id,
                    reply_user_rec.username,
                    current_timestamp,
                    reply_user_rec.id
                );
                
                total_messages := total_messages + 1;
                thread_start_timestamp := current_timestamp; -- For next reply in thread
            END LOOP;
        END LOOP;
    END LOOP;
    
    -- Add message views (70% of messages are viewed by someone)
    INSERT INTO message_view (message_id, user_id, viewed_at)
    SELECT 
        m.id, 
        ufm.user_id,
        m.timestamp + (random() * 24 * interval '1 hour')
    FROM 
        message m
        JOIN user_family_membership ufm ON m.family_id = ufm.family_id
    WHERE 
        random() < 0.7 -- 70% chance of being viewed
        AND ufm.user_id != m.sender_id; -- Don't view your own message
    
    -- Add some message reactions (20% of messages get reactions)
    INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
    SELECT 
        m.id, 
        ufm.user_id,
        CASE floor(random() * 5)
            WHEN 0 THEN 'LIKE'
            WHEN 1 THEN 'LOVE'
            WHEN 2 THEN 'HAHA'
            WHEN 3 THEN 'WOW'
            ELSE 'SAD'
        END,
        m.timestamp + (random() * 48 * interval '1 hour')
    FROM 
        message m
        JOIN user_family_membership ufm ON m.family_id = ufm.family_id
    WHERE 
        random() < 0.2 -- 20% chance of reaction
        AND ufm.user_id != m.sender_id; -- Don't react to your own message
    
    -- Print summary
    RAISE NOTICE 'Generated % total messages across % threads', total_messages, thread_count;
END $$;

-- Add message comments (10% of messages get comments)
DO $$
DECLARE
    message_rec RECORD;
    user_rec RECORD;
    comment_count INT;
    comment_content TEXT[] := ARRAY[
        'Great message!',
        'I completely agree with you',
        'Thanks for sharing this',
        'This is interesting',
        'I was just thinking about this',
        'We should talk more about this',
        'I have more to add on this topic',
        'Reminds me of when we were kids',
        'I''ll call you later about this',
        'This is exactly what I needed today',
        'You always know just what to say',
        'I''m saving this message',
        'Well said!',
        'I couldn''t have said it better myself',
        'You made my day with this',
        'This is important for everyone to see',
        'I''m showing this to everyone',
        'Classic you!',
        'This is why I love our family chat',
        'Best message of the day'
    ];
BEGIN
    -- Loop through messages that will have comments
    FOR message_rec IN (
        SELECT m.id, m.family_id, m.timestamp
        FROM message m
        WHERE random() < 0.1 -- 10% of messages get comments
    ) LOOP
        -- 1-3 comments per message
        comment_count := floor(random() * 3) + 1;
        
        -- Add comments
        FOR i IN 1..comment_count LOOP
            -- Get a random user from the same family (not the message sender)
            SELECT u.id
            INTO user_rec
            FROM app_user u
            JOIN user_family_membership ufm ON u.id = ufm.user_id
            WHERE ufm.family_id = message_rec.family_id
            ORDER BY random()
            LIMIT 1;
            
            -- Insert comment
            INSERT INTO message_comment (message_id, user_id, content, created_at)
            VALUES (
                message_rec.id,
                user_rec.id,
                comment_content[floor(random() * array_length(comment_content, 1)) + 1],
                message_rec.timestamp + (random() * 72 * interval '1 hour')
            );
        END LOOP;
    END LOOP;
END $$;

-- Clean up temporary function
DROP FUNCTION IF EXISTS random_timestamp(timestamp, timestamp);

-- Print summary statistics
SELECT 'Messages' AS type, COUNT(*) AS count FROM message
UNION ALL
SELECT 'Message Views', COUNT(*) FROM message_view
UNION ALL
SELECT 'Message Reactions', COUNT(*) FROM message_reaction
UNION ALL
SELECT 'Message Comments', COUNT(*) FROM message_comment
UNION ALL
SELECT 'Messages with Media', COUNT(*) FROM message WHERE media_type IS NOT NULL;

-- Update sequence to last value used
SELECT setval('message_id_seq', (SELECT MAX(id) FROM message), true); 