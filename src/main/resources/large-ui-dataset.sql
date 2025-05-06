-- Large UI Test Dataset for FamilyNest
-- This script creates a much larger dataset specifically for testing UI performance
-- with scrolling lists, pagination, etc.

-- Clear existing test data
DELETE FROM user_member_message_settings;
DELETE FROM user_family_message_settings;
DELETE FROM user_family_membership;
DELETE FROM family;
DELETE FROM app_user;

-- Create large batch of test users (100+ users)
INSERT INTO app_user (id, username, password, email, first_name, last_name, role, photo, phone_number)
VALUES 
-- First 20 users (with details)
(101, 'john.doe', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'john.doe@example.com', 'John', 'Doe', 'USER', NULL, '555-0101'),
(102, 'jane.doe', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'jane.doe@example.com', 'Jane', 'Doe', 'USER', NULL, '555-0102'),
(103, 'bob.smith', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'bob.smith@example.com', 'Bob', 'Smith', 'USER', NULL, '555-0103'),
(104, 'alice.johnson', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'alice.johnson@example.com', 'Alice', 'Johnson', 'USER', NULL, '555-0104'),
(105, 'charlie.brown', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'charlie.brown@example.com', 'Charlie', 'Brown', 'USER', NULL, '555-0105'),
(106, 'diana.prince', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'diana.prince@example.com', 'Diana', 'Prince', 'USER', NULL, '555-0106'),
(107, 'edward.lewis', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'edward.lewis@example.com', 'Edward', 'Lewis', 'USER', NULL, '555-0107'),
(108, 'fiona.gallagher', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'fiona.gallagher@example.com', 'Fiona', 'Gallagher', 'USER', NULL, '555-0108'),
(109, 'george.wilson', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'george.wilson@example.com', 'George', 'Wilson', 'USER', NULL, '555-0109'),
(110, 'hannah.baker', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'hannah.baker@example.com', 'Hannah', 'Baker', 'USER', NULL, '555-0110'),
(111, 'ian.chen', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'ian.chen@example.com', 'Ian', 'Chen', 'USER', NULL, '555-0111'),
(112, 'jessica.day', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'jessica.day@example.com', 'Jessica', 'Day', 'USER', NULL, '555-0112'),
(113, 'kevin.hart', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'kevin.hart@example.com', 'Kevin', 'Hart', 'USER', NULL, '555-0113'),
(114, 'lisa.simpson', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'lisa.simpson@example.com', 'Lisa', 'Simpson', 'USER', NULL, '555-0114'),
(115, 'michael.scott', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'michael.scott@example.com', 'Michael', 'Scott', 'USER', NULL, '555-0115'),
(116, 'nancy.wheeler', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'nancy.wheeler@example.com', 'Nancy', 'Wheeler', 'USER', NULL, '555-0116'),
(117, 'oliver.queen', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'oliver.queen@example.com', 'Oliver', 'Queen', 'USER', NULL, '555-0117'),
(118, 'patricia.johnson', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'patricia.johnson@example.com', 'Patricia', 'Johnson', 'USER', NULL, '555-0118'),
(119, 'quincy.jones', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'quincy.jones@example.com', 'Quincy', 'Jones', 'USER', NULL, '555-0119'),
(120, 'rachel.green', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'rachel.green@example.com', 'Rachel', 'Green', 'USER', NULL, '555-0120');

-- Generate 80 more users in bulk (with systematic naming)
DO $$
DECLARE
  i INT := 121;
  first_name TEXT;
  last_name TEXT;
  username TEXT;
  email TEXT;
BEGIN
  WHILE i <= 200 LOOP
    first_name := 'User' || (i - 100);
    last_name := 'Test' || (i - 100);
    username := 'user' || i;
    email := 'user' || i || '@example.com';
    
    INSERT INTO app_user (id, username, password, email, first_name, last_name, role, photo, phone_number)
    VALUES (
      i, 
      username, 
      '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 
      email, 
      first_name, 
      last_name, 
      'USER', 
      NULL, 
      '555-' || LPAD(i::text, 4, '0')
    );
    i := i + 1;
  END LOOP;
END $$;

-- Create 20 large families
DO $$
DECLARE
  i INT := 1;
  family_name TEXT;
  owner_id BIGINT;
BEGIN
  WHILE i <= 20 LOOP
    -- Determine family name
    CASE 
      WHEN i = 1 THEN family_name := 'The Doe Family';
      WHEN i = 2 THEN family_name := 'Smith Family';
      WHEN i = 3 THEN family_name := 'Johnson Clan';
      WHEN i = 4 THEN family_name := 'The Brown Household';
      WHEN i = 5 THEN family_name := 'Prince Dynasty';
      WHEN i = 6 THEN family_name := 'Lewis Family';
      WHEN i = 7 THEN family_name := 'Gallagher House';
      WHEN i = 8 THEN family_name := 'Wilson & Co';
      WHEN i = 9 THEN family_name := 'Baker Family';
      WHEN i = 10 THEN family_name := 'Chen Family';
      ELSE family_name := 'Test Family ' || i;
    END CASE;
    
    -- Owner is based on family number, cycled through first 20 users
    owner_id := 100 + (i % 20) + 1;
    
    -- Create the family
    INSERT INTO family (id, name, created_by)
    VALUES (200 + i, family_name, owner_id);
    
    i := i + 1;
  END LOOP;
END $$;

-- Create complex family memberships
-- Each family will have 10-30 members with different roles
DO $$
DECLARE
  f INT;
  member_count INT;
  j INT;
  current_family_id BIGINT;
  current_owner_id BIGINT;
  current_user_id BIGINT;
  role_value VARCHAR(10);
  random_val FLOAT;
BEGIN
  -- For each family
  f := 1;
  WHILE f <= 20 LOOP
    current_family_id := 200 + f;
    
    -- Get the owner ID for this family
    SELECT created_by INTO current_owner_id FROM family WHERE id = current_family_id;
    
    -- Decide how many members (10-30)
    member_count := 10 + floor(random() * 20);
    
    -- Add the owner with OWNER role
    INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
    VALUES (current_family_id, current_owner_id, 'OWNER', true, NOW());
    
    -- Add remaining members
    j := 1;
    WHILE j <= member_count LOOP
      -- Select a random user (not the owner)
      current_user_id := 100 + floor(1 + random() * 99);
      IF current_user_id != current_owner_id THEN
        -- Assign role (20% ADMIN, 80% MEMBER)
        random_val := random();
        IF random_val < 0.2 THEN
          role_value := 'ADMIN';
        ELSE
          role_value := 'MEMBER';
        END IF;
        
        -- Insert if not already a member
        IF NOT EXISTS (
          SELECT 1 FROM user_family_membership 
          WHERE family_id = current_family_id AND user_id = current_user_id
        ) THEN
          INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
          VALUES (current_family_id, current_user_id, role_value, true, NOW());
        END IF;
      END IF;
      j := j + 1;
    END LOOP;
    
    f := f + 1;
  END LOOP;
END $$;

-- Create diverse family message preferences
DO $$
DECLARE
  current_user_id BIGINT;
  current_family_id BIGINT;
  random_val FLOAT;
BEGIN
  FOR current_user_id, current_family_id IN
    SELECT DISTINCT user_id, family_id FROM user_family_membership
  LOOP
    -- 70% chance to create an explicit preference
    random_val := random();
    IF random_val < 0.7 THEN
      -- 80% true, 20% false
      IF random() < 0.8 THEN
        INSERT INTO user_family_message_settings (user_id, family_id, receive_messages)
        VALUES (current_user_id, current_family_id, true);
      ELSE
        INSERT INTO user_family_message_settings (user_id, family_id, receive_messages)
        VALUES (current_user_id, current_family_id, false);
      END IF;
    END IF;
  END LOOP;
END $$;

-- Create member message preferences
DO $$
DECLARE
  current_family_id BIGINT;
BEGIN
  FOR current_family_id IN
    SELECT DISTINCT id FROM family
  LOOP
    -- For each family, create member preferences
    INSERT INTO user_member_message_settings (user_id, member_user_id, family_id, receive_messages)
    SELECT 
      m1.user_id, 
      m2.user_id, 
      current_family_id, 
      -- 80% true, 20% false
      random() < 0.8
    FROM 
      user_family_membership m1
      JOIN user_family_membership m2 ON m1.family_id = m2.family_id AND m1.user_id != m2.user_id
    WHERE 
      m1.family_id = current_family_id 
      -- Only generate for ~30% of possible pairs to avoid overwhelming the database
      AND random() < 0.3;
  END LOOP;
END $$;

-- Print summary statistics
SELECT 'Users' AS type, COUNT(*) AS count FROM app_user
UNION ALL
SELECT 'Families', COUNT(*) FROM family
UNION ALL
SELECT 'Family Memberships', COUNT(*) FROM user_family_membership
UNION ALL
SELECT 'Family Message Preferences', COUNT(*) FROM user_family_message_settings
UNION ALL
SELECT 'Member Message Preferences', COUNT(*) FROM user_member_message_settings; 