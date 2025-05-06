-- Large Message Thread for Load Testing
-- This script adds a single large thread with 1000+ messages for load testing

-- Create a large message thread with many consecutive messages
DO $$
DECLARE
    family_id_var BIGINT;
    message_id BIGINT;
    original_message_id BIGINT;
    user_id BIGINT;
    username TEXT;
    message_count INT := 1000;  -- Increased from 100 to 1000 messages
    current_ts TIMESTAMP;  -- Renamed to avoid conflict with PostgreSQL function
    thread_start_timestamp TIMESTAMP := '2025-06-01 08:00:00'::TIMESTAMP;
    message_content TEXT;
    user_cursor REFCURSOR;
    user_rec RECORD;
    content_index INT;
    
    -- Message content for large thread
    message_contents TEXT[] := ARRAY[
        'Has anyone seen the new family photo album app?',
        'I just installed it and it looks promising!',
        'What features does it have?',
        'It automatically organizes photos by date and can recognize faces!',
        'That sounds really useful for our family reunion photos',
        'Does it cost anything to use?',
        'There''s a free version with basic features',
        'But the premium version has more storage and better organization',
        'I think it would be worth it for our family',
        'We have SO many photos from all our events',
        'I still have boxes of old photos we need to digitize',
        'We should plan a scanning day to get those all uploaded',
        'Great idea! We could do it at the next gathering',
        'When is everyone free next month?',
        'I can host at my place if people want to come over',
        'That would be great, your place has more space',
        'Should we make it a potluck as well?',
        'I can bring my famous casserole',
        'You know I can''t resist your casserole!',
        'I''ll bring dessert then',
        'I can bring some drinks and snacks',
        'This is turning into a real party!',
        'We should invite the cousins too',
        'Let me check if they''re available',
        'They said they''re free and excited to join',
        'That''s great news! I haven''t seen them in months',
        'Should we do any other activities while we''re together?',
        'Maybe we could watch some of the old family videos?',
        'That would be fun! I found some from the 90s recently',
        'Those haircuts are going to be hilarious to see again',
        'Please don''t remind me of my bowl cut phase',
        'Too late, I already have screenshots ready to share',
        'I''m never going to live that down, am I?',
        'Not as long as I have access to those photos!',
        'I think we all had questionable style choices back then',
        'True, I remember those neon windbreakers everyone wore',
        'And the platform shoes!',
        'I actually still have a pair in my closet...',
        'No way, you have to wear them to the gathering!',
        'Only if you wear your old band t-shirts',
        'Deal! This is going to be quite the throwback day',
        'We should all dress up in our best 90s outfits',
        'That''s actually a brilliant theme idea',
        'I''ll start digging through my storage boxes',
        'I think mom still has some of our old clothes',
        'I''ll ask her to bring them over',
        'This is going to be the best family gathering ever',
        'I''m so excited to see everyone and reminisce',
        'Should we extend it to a whole weekend?',
        'That would give us more time to go through everything',
        'I could take Friday off work',
        'Same here, a three-day family weekend sounds perfect',
        'Let''s make it official then',
        'I''ll create an online invitation',
        'Great, just make sure to include all the details',
        'And don''t forget to mention the 90s dress code!',
        'Should we make a shared playlist of 90s music too?',
        'Absolutely! I still remember all the lyrics',
        'We need to include that song we used to dance to',
        'The one with the ridiculous dance moves?',
        'That''s the one! I can still do the whole routine',
        'Now that I have to see',
        'Be careful what you wish for!',
        'I''m looking forward to it all',
        'This thread is getting so long, we should make a group chat',
        'Good idea, it''s easier to coordinate there',
        'I''ll set one up after this',
        'Perfect, just add everyone from the family',
        'Will do! This is going to be amazing',
        'I can''t wait to see everyone''s 90s outfits',
        'Mine is going to be the most embarrassing for sure',
        'We''ll have a contest for that',
        'With a prize for the most authentic look',
        'I''m definitely going to win that one',
        'Not if I find my old platform sneakers!',
        'The competition is getting fierce already',
        'We should document the whole day',
        'And add it to our new photo app',
        'Full circle back to where we started',
        'This family is the best',
        'Agreed! Love you all',
        'See everyone next month!',
        'Don''t forget to RSVP on the invitation I''ll send',
        'And start practicing those dance moves',
        'Already on it!',
        'This is going to be legendary',
        'See you all soon!',
        'I found more old photos in the attic',
        'You won''t believe how young grandma looks',
        'We should scan those first',
        'I have a high-speed scanner we can use',
        'Perfect! That will save us so much time',
        'Has anyone contacted uncle Joe?',
        'Yes, he said he might fly in for it',
        'That would be amazing, haven''t seen him in years',
        'He said he has some old family films too',
        'From the old 8mm camera grandpa used to have?',
        'Exactly! We should get those digitized',
        'I know a place that can convert those',
        'How much would that cost?',
        'Not too bad if we do them all at once',
        'We could split the cost between us',
        'That sounds fair to me',
        'Count me in for my share',
        'Me too, those are priceless memories',
        'Who else needs to be invited?',
        'What about the cousins from the west coast?',
        'Yes, though they might not be able to make it',
        'We should video call them during the event',
        'Great idea! We can show them all the old stuff',
        'I bet they''ll be jealous they missed it',
        'Maybe they''ll join next time',
        'We should make this an annual thing',
        'The Great Family Archive Day',
        'I love that name!'
    ];
BEGIN
    -- Get a family with active users
    SELECT id INTO family_id_var FROM family ORDER BY id LIMIT 1;
    
    -- Get message ID for the thread starter
    SELECT nextval('message_id_seq') INTO original_message_id;
    message_id := original_message_id;
    
    -- Get a user to start the thread
    SELECT u.id, u.username 
    INTO user_id, username
    FROM app_user u
    JOIN user_family_membership fm ON u.id = fm.user_id
    WHERE fm.family_id = family_id_var
    ORDER BY RANDOM()
    LIMIT 1;
    
    -- Insert thread starter message
    INSERT INTO message (id, content, family_id, media_type, media_url, sender_id, sender_username, timestamp, user_id)
    VALUES (
        message_id,
        'This is the start of our load testing thread - we''ll be discussing our upcoming family reunion and photo digitization project!',
        family_id_var,
        NULL,
        NULL,
        user_id,
        username,
        thread_start_timestamp,
        user_id
    );
    
    -- Add all the reply messages with different users
    current_ts := thread_start_timestamp;
    
    FOR i IN 1..message_count LOOP
        -- Get next message ID
        SELECT nextval('message_id_seq') INTO message_id;
        
        -- Get a random user from this family
        OPEN user_cursor FOR 
            SELECT u.id, u.username
            FROM app_user u
            JOIN user_family_membership fm ON u.id = fm.user_id
            WHERE fm.family_id = family_id_var
            ORDER BY RANDOM()
            LIMIT 1;
        
        FETCH user_cursor INTO user_rec;
        CLOSE user_cursor;
        
        -- Advance timestamp realistically (1-20 minutes between messages)
        current_ts := current_ts + (random() * 19 + 1) * interval '1 minute';
        
        -- Calculate content index (cycle through available messages if i > array length)
        content_index := 1 + (i - 1) % array_length(message_contents, 1);
        
        -- Insert message
        INSERT INTO message (id, content, family_id, media_type, media_url, sender_id, sender_username, timestamp, user_id)
        VALUES (
            message_id,
            message_contents[content_index],
            family_id_var,
            NULL,
            NULL,
            user_rec.id,
            user_rec.username,
            current_ts,
            user_rec.id
        );
        
        -- Add views for most messages
        INSERT INTO message_view (message_id, user_id, viewed_at)
        SELECT 
            message_id, 
            u.id,
            current_ts + (random() * 10) * interval '1 minute'
        FROM 
            app_user u
            JOIN user_family_membership fm ON u.id = fm.user_id
        WHERE 
            fm.family_id = family_id_var
            AND u.id != user_rec.id
            AND random() < 0.8; -- 80% chance of users seeing each message
        
        -- Add reactions (30% chance per user)
        INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
        SELECT 
            message_id, 
            u.id,
            CASE floor(random() * 5)
                WHEN 0 THEN 'LIKE'
                WHEN 1 THEN 'LOVE'
                WHEN 2 THEN 'HAHA'
                WHEN 3 THEN 'WOW'
                ELSE 'SAD'
            END,
            current_ts + (random() * 15) * interval '1 minute'
        FROM 
            app_user u
            JOIN user_family_membership fm ON u.id = fm.user_id
        WHERE 
            fm.family_id = family_id_var
            AND u.id != user_rec.id
            AND random() < 0.3; -- 30% chance of reaction per user
    END LOOP;
    
    -- Add some comments to random messages in the thread
    FOR i IN 1..(message_count/10) LOOP -- Add comments to ~10% of messages
        -- Select a random message from our thread
        SELECT id, timestamp 
        INTO message_id, current_ts
        FROM message 
        WHERE id BETWEEN original_message_id AND message_id
        ORDER BY RANDOM() 
        LIMIT 1;
        
        -- Get a random user to comment
        SELECT u.id
        INTO user_id
        FROM app_user u
        JOIN user_family_membership fm ON u.id = fm.user_id
        WHERE fm.family_id = family_id_var
        ORDER BY RANDOM()
        LIMIT 1;
        
        -- Insert comment
        INSERT INTO message_comment (message_id, user_id, content, created_at)
        VALUES (
            message_id,
            user_id,
            CASE floor(random() * 5)
                WHEN 0 THEN 'Great idea!'
                WHEN 1 THEN 'I completely agree'
                WHEN 2 THEN 'This makes me so happy'
                WHEN 3 THEN 'Looking forward to it'
                ELSE 'Can''t wait to see everyone'
            END,
            current_ts + (random() * 20) * interval '1 minute'
        );
    END LOOP;
    
    -- Print summary
    RAISE NOTICE 'Created large thread with % messages in family %', message_count + 1, family_id_var;
    
END $$;

-- Update sequence to last value used
SELECT setval('message_id_seq', (SELECT MAX(id) FROM message), true);

-- Print statistics for the large thread
SELECT 'Large Thread Messages' AS type, COUNT(*) AS count 
FROM message 
WHERE id > (SELECT MIN(id) FROM message WHERE content LIKE 'This is the start of our load testing thread%')
UNION ALL
SELECT 'Large Thread Views', COUNT(*) 
FROM message_view mv
JOIN message m ON mv.message_id = m.id
WHERE m.content LIKE 'This is the start of our load testing thread%' OR m.id > (SELECT MIN(id) FROM message WHERE content LIKE 'This is the start of our load testing thread%')
UNION ALL
SELECT 'Large Thread Reactions', COUNT(*) 
FROM message_reaction mr
JOIN message m ON mr.message_id = m.id
WHERE m.content LIKE 'This is the start of our load testing thread%' OR m.id > (SELECT MIN(id) FROM message WHERE content LIKE 'This is the start of our load testing thread%')
UNION ALL
SELECT 'Large Thread Comments', COUNT(*) 
FROM message_comment mc
JOIN message m ON mc.message_id = m.id
WHERE m.content LIKE 'This is the start of our load testing thread%' OR m.id > (SELECT MIN(id) FROM message WHERE content LIKE 'This is the start of our load testing thread%'); 