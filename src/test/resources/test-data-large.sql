-- Large Test Dataset for Message Preferences
-- This script creates a large dataset with various family structures
-- and message preferences to enable thorough testing

-- Clear existing test data
DELETE FROM user_member_message_settings;
DELETE FROM user_family_message_settings;
DELETE FROM user_family_membership;
DELETE FROM family;
DELETE FROM app_user;

-- Create test users (50 users with various names and roles)
INSERT INTO app_user (id, username, password, email, first_name, last_name, role, photo, phone_number)
VALUES 
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
(115, 'michael.scott', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'michael.scott@example.com', 'Michael', 'Scott', 'USER', NULL, '555-0115');

-- Additional users (continue pattern up to 50)
INSERT INTO app_user (id, username, password, email, first_name, last_name, role, photo, phone_number)
VALUES 
(116, 'nancy.wheeler', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'nancy.wheeler@example.com', 'Nancy', 'Wheeler', 'USER', NULL, '555-0116'),
(117, 'oliver.queen', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'oliver.queen@example.com', 'Oliver', 'Queen', 'USER', NULL, '555-0117'),
(118, 'patricia.johnson', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'patricia.johnson@example.com', 'Patricia', 'Johnson', 'USER', NULL, '555-0118'),
(119, 'quincy.jones', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'quincy.jones@example.com', 'Quincy', 'Jones', 'USER', NULL, '555-0119'),
(120, 'rachel.green', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'rachel.green@example.com', 'Rachel', 'Green', 'USER', NULL, '555-0120');

-- Create diverse family structures (10 families with various sizes)
INSERT INTO family (id, name, created_by)
VALUES 
(201, 'The Doe Family', 101),        -- John Doe's family
(202, 'Smith Family', 103),          -- Bob Smith's family
(203, 'Johnson Clan', 104),          -- Alice Johnson's family
(204, 'The Brown Household', 105),   -- Charlie Brown's family
(205, 'Prince Dynasty', 106),        -- Diana Prince's family
(206, 'Lewis Family', 107),          -- Edward Lewis's family
(207, 'Gallagher House', 108),       -- Fiona Gallagher's family
(208, 'Wilson & Co', 109),           -- George Wilson's family
(209, 'Baker Family', 110),          -- Hannah Baker's family
(210, 'Chen Family', 111);           -- Ian Chen's family

-- Complex family memberships (multiple users in multiple families with different roles)
-- The Doe Family members
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
(201, 101, 'OWNER', true, CURRENT_TIMESTAMP),    -- John (owner)
(201, 102, 'ADMIN', true, CURRENT_TIMESTAMP),    -- Jane
(201, 103, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Bob
(201, 104, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Alice
(201, 105, 'MEMBER', true, CURRENT_TIMESTAMP);   -- Charlie

-- Smith Family members
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
(202, 103, 'OWNER', true, CURRENT_TIMESTAMP),    -- Bob (owner)
(202, 102, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Jane
(202, 106, 'ADMIN', true, CURRENT_TIMESTAMP),    -- Diana
(202, 107, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Edward
(202, 108, 'MEMBER', true, CURRENT_TIMESTAMP);   -- Fiona

-- Johnson Clan members
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
(203, 104, 'OWNER', true, CURRENT_TIMESTAMP),    -- Alice (owner)
(203, 105, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Charlie
(203, 106, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Diana
(203, 109, 'ADMIN', true, CURRENT_TIMESTAMP),    -- George
(203, 110, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Hannah
(203, 111, 'MEMBER', true, CURRENT_TIMESTAMP);   -- Ian

-- Brown Household members
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
(204, 105, 'OWNER', true, CURRENT_TIMESTAMP),    -- Charlie (owner)
(204, 112, 'ADMIN', true, CURRENT_TIMESTAMP),    -- Jessica
(204, 113, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Kevin
(204, 114, 'MEMBER', true, CURRENT_TIMESTAMP);   -- Lisa

-- Prince Dynasty members
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
(205, 106, 'OWNER', true, CURRENT_TIMESTAMP),    -- Diana (owner)
(205, 115, 'ADMIN', true, CURRENT_TIMESTAMP),    -- Michael
(205, 116, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Nancy
(205, 117, 'MEMBER', true, CURRENT_TIMESTAMP),   -- Oliver
(205, 101, 'MEMBER', true, CURRENT_TIMESTAMP);   -- John (member in this family)

-- Family message preferences (mix of enabled and disabled)
INSERT INTO user_family_message_settings (user_id, family_id, receive_messages)
VALUES 
-- John Doe's preferences
(101, 201, true),    -- John receives messages from his own family
(101, 205, false),   -- John doesn't receive messages from Diana's family

-- Jane Doe's preferences
(102, 201, true),    -- Jane receives messages from Doe family
(102, 202, false),   -- Jane doesn't receive messages from Smith family

-- Bob Smith's preferences
(103, 201, true),    -- Bob receives messages from Doe family
(103, 202, true),    -- Bob receives messages from his own Smith family

-- Alice Johnson's preferences
(104, 201, false),   -- Alice doesn't receive messages from Doe family
(104, 203, true),    -- Alice receives messages from her own Johnson family

-- Charlie Brown's preferences
(105, 201, true),    -- Charlie receives messages from Doe family
(105, 203, true),    -- Charlie receives messages from Johnson family
(105, 204, true);    -- Charlie receives messages from his own Brown family

-- Member message preferences (customize who receives from whom)
INSERT INTO user_member_message_settings (user_id, family_id, member_user_id, receive_messages)
VALUES 
-- John Doe's member preferences in Doe family
(101, 201, 102, true),     -- John receives from Jane
(101, 201, 103, true),     -- John receives from Bob
(101, 201, 104, false),    -- John doesn't receive from Alice
(101, 201, 105, true),     -- John receives from Charlie

-- Jane Doe's member preferences in Doe family
(102, 201, 101, true),     -- Jane receives from John
(102, 201, 103, false),    -- Jane doesn't receive from Bob
(102, 201, 104, true),     -- Jane receives from Alice
(102, 201, 105, false),    -- Jane doesn't receive from Charlie

-- Alice Johnson's member preferences in Johnson family
(104, 203, 105, false),    -- Alice doesn't receive from Charlie
(104, 203, 106, true),     -- Alice receives from Diana
(104, 203, 109, true),     -- Alice receives from George
(104, 203, 110, true),     -- Alice receives from Hannah
(104, 203, 111, true),     -- Alice receives from Ian

-- Bob Smith's member preferences in Smith family
(103, 202, 102, true),     -- Bob receives from Jane
(103, 202, 106, true),     -- Bob receives from Diana
(103, 202, 107, false),    -- Bob doesn't receive from Edward
(103, 202, 108, true);     -- Bob receives from Fiona

-- Add more complex scenarios: users with multiple family memberships
INSERT INTO user_family_membership (family_id, user_id, role, is_active, joined_at)
VALUES 
-- Alice is also in Baker family
(209, 104, 'MEMBER', true, CURRENT_TIMESTAMP),
-- Bob is also in Chen family
(210, 103, 'ADMIN', true, CURRENT_TIMESTAMP),
-- John is in multiple families
(202, 101, 'MEMBER', true, CURRENT_TIMESTAMP),
(203, 101, 'MEMBER', true, CURRENT_TIMESTAMP),
-- Jessica is in multiple families
(206, 112, 'MEMBER', true, CURRENT_TIMESTAMP),
(207, 112, 'ADMIN', true, CURRENT_TIMESTAMP),
(208, 112, 'MEMBER', true, CURRENT_TIMESTAMP);

-- Add family preferences for users in multiple families
INSERT INTO user_family_message_settings (user_id, family_id, receive_messages)
VALUES 
-- John's additional family preferences
(101, 202, true),     -- John receives from Smith family
(101, 203, false),    -- John doesn't receive from Johnson family

-- Alice's additional family preferences
(104, 209, true),     -- Alice receives from Baker family

-- Jessica's family preferences
(112, 204, true),     -- Jessica receives from Brown family
(112, 206, false),    -- Jessica doesn't receive from Lewis family
(112, 207, true),     -- Jessica receives from Gallagher family
(112, 208, false);    -- Jessica doesn't receive from Wilson family 