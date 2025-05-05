-- populate_test_data.sql
-- Script to populate the test database with sample data for development and testing

-- Create 20 test users (testuser1 through testuser20)
DO $$
DECLARE
    i INT;
    user_id BIGINT;
    password_hash VARCHAR := '$2a$10$h.dl5J86rGH7I8bD9bZeZeri3YeW5q1mHQQzMuV1QEvt0U4Ex.9tK'; -- 'password' hashed with bcrypt
BEGIN
    FOR i IN 1..20 LOOP
        -- Check if user already exists
        IF NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'testuser' || i) THEN
            -- Insert the user
            INSERT INTO app_user (username, password, email, first_name, last_name, role)
            VALUES (
                'testuser' || i,
                password_hash,
                'testuser' || i || '@example.com',
                'Test',
                'User' || i,
                'USER'
            )
            RETURNING id INTO user_id;
            
            RAISE NOTICE 'Created test user ID: %', user_id;
        END IF;
    END LOOP;
END $$;

-- Create 10 test families (Family1 through Family10)
DO $$
DECLARE
    i INT;
    creator_id BIGINT;
    family_id BIGINT;
BEGIN
    FOR i IN 1..10 LOOP
        -- Get a user to be family creator (testuser1 through testuser10)
        SELECT id INTO creator_id FROM app_user WHERE username = 'testuser' || i LIMIT 1;
        
        -- Check if family already exists for this creator
        IF NOT EXISTS (SELECT 1 FROM family WHERE created_by = creator_id) THEN
            -- Insert the family
            INSERT INTO family (name, created_by)
            VALUES ('Family' || i, creator_id)
            RETURNING id INTO family_id;
            
            -- Add the creator as a member with ADMIN role
            INSERT INTO user_family_membership (user_id, family_id, role, is_active)
            VALUES (creator_id, family_id, 'ADMIN', true);
            
            RAISE NOTICE 'Created family ID: % with creator ID: %', family_id, creator_id;
        END IF;
    END LOOP;
END $$;

-- Add members to families
-- User1's family gets members from testuser11-20
-- User2's family gets members from testuser11-20
DO $$
DECLARE
    user1_id BIGINT;
    user2_id BIGINT;
    family1_id BIGINT;
    family2_id BIGINT;
    member_id BIGINT;
    i INT;
BEGIN
    -- Get User IDs
    SELECT id INTO user1_id FROM app_user WHERE username = 'testuser1';
    SELECT id INTO user2_id FROM app_user WHERE username = 'testuser2';
    
    -- Get Family IDs
    SELECT id INTO family1_id FROM family WHERE created_by = user1_id;
    SELECT id INTO family2_id FROM family WHERE created_by = user2_id;
    
    RAISE NOTICE 'User1 ID: %, Family1 ID: %', user1_id, family1_id;
    RAISE NOTICE 'User2 ID: %, Family2 ID: %', user2_id, family2_id;
    
    -- Add members to User1's family
    FOR i IN 11..20 LOOP
        SELECT id INTO member_id FROM app_user WHERE username = 'testuser' || i;
        
        -- Check if membership already exists
        IF NOT EXISTS (SELECT 1 FROM user_family_membership WHERE user_id = member_id AND family_id = family1_id) THEN
            -- Add membership
            INSERT INTO user_family_membership (user_id, family_id, role, is_active)
            VALUES (member_id, family1_id, 'MEMBER', true);
            
            RAISE NOTICE 'Added member % to family %', member_id, family1_id;
        END IF;
    END LOOP;
    
    -- Add members to User2's family
    FOR i IN 11..20 LOOP
        SELECT id INTO member_id FROM app_user WHERE username = 'testuser' || i;
        
        -- Check if membership already exists
        IF NOT EXISTS (SELECT 1 FROM user_family_membership WHERE user_id = member_id AND family_id = family2_id) THEN
            -- Add membership
            INSERT INTO user_family_membership (user_id, family_id, role, is_active)
            VALUES (member_id, family2_id, 'MEMBER', true);
            
            RAISE NOTICE 'Added member % to family %', member_id, family2_id;
        END IF;
    END LOOP;
END $$;

-- Create some additional memberships (cross-family relationships)
DO $$
DECLARE
    i INT;
    j INT;
    user_id BIGINT;
    family_id BIGINT;
BEGIN
    -- Add some users 3-10 to other families for more complex relationships
    FOR i IN 3..10 LOOP
        SELECT id INTO user_id FROM app_user WHERE username = 'testuser' || i;
        
        -- Add this user to 2 random families (not their own)
        FOR j IN 1..2 LOOP
            -- Get a random family
            SELECT f.id INTO family_id
            FROM family f
            WHERE f.created_by != user_id
            AND NOT EXISTS (
                SELECT 1 FROM user_family_membership 
                WHERE user_id = user_id AND family_id = f.id
            )
            ORDER BY RANDOM() 
            LIMIT 1;
            
            -- If we found a family, add membership
            IF family_id IS NOT NULL THEN
                INSERT INTO user_family_membership (user_id, family_id, role, is_active)
                VALUES (user_id, family_id, 'MEMBER', true);
                
                RAISE NOTICE 'Added cross-family membership: User % to Family %', user_id, family_id;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- Add some test messages in each family
DO $$
DECLARE
    family_record RECORD;
    user_record RECORD;
    message_count INT;
BEGIN
    FOR family_record IN SELECT id FROM family LOOP
        message_count := 0;
        
        -- For each member in this family
        FOR user_record IN 
            SELECT m.user_id 
            FROM user_family_membership m 
            WHERE m.family_id = family_record.id 
        LOOP
            -- Add 1-3 messages from this user in this family
            FOR i IN 1..floor(random() * 3 + 1)::int LOOP
                INSERT INTO message (user_id, content, family_id, timestamp)
                VALUES (
                    user_record.user_id,
                    'Test message ' || i || ' from user ' || user_record.user_id || ' in family ' || family_record.id,
                    family_record.id,
                    NOW() - (random() * interval '10 days')
                );
                
                message_count := message_count + 1;
            END LOOP;
        END LOOP;
        
        RAISE NOTICE 'Added % messages to family %', message_count, family_record.id;
    END LOOP;
END $$; 