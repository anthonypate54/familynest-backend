-- Test data script for H2 test database
-- This is automatically executed by Spring Boot when the test profile is active

-- Reset sequences (must use H2 syntax for sequences)
ALTER TABLE app_user ALTER COLUMN id RESTART WITH 1;
ALTER TABLE family ALTER COLUMN id RESTART WITH 1;
ALTER TABLE message ALTER COLUMN id RESTART WITH 1;
ALTER TABLE comment ALTER COLUMN id RESTART WITH 1;
ALTER TABLE reaction ALTER COLUMN id RESTART WITH 1;
-- Removed message_view table - no longer exists

-- Add test admin user
INSERT INTO app_user (id, username, email, password, first_name, last_name, role, enabled) 
VALUES (1, 'testadmin', 'testadmin@example.com', '{noop}password', 'Test', 'Admin', 'ADMIN', true);

-- Add additional test users
INSERT INTO app_user (id, username, email, password, first_name, last_name, role, enabled) 
VALUES 
  (101, 'user101', 'user101@example.com', '{noop}password', 'User', 'One', 'USER', true),
  (102, 'user102', 'user102@example.com', '{noop}password', 'User', 'Two', 'USER', true),
  (103, 'user103', 'user103@example.com', '{noop}password', 'User', 'Three', 'USER', true),
  (104, 'user104', 'user104@example.com', '{noop}password', 'User', 'Four', 'USER', true);

-- Add 2 test families
INSERT INTO family (id, name, created_by) 
VALUES 
  (1, 'Test Family 1', 1),
  (2, 'Test Family 2', 1);

-- Add family memberships for test users
INSERT INTO user_family_membership (user_id, family_id, role, is_active) 
VALUES 
  (1, 1, 'ADMIN', true),
  (101, 1, 'MEMBER', true),
  (102, 1, 'MEMBER', true),
  (103, 1, 'MEMBER', true),
  (104, 2, 'MEMBER', true);

-- Add test messages for engagement tests
INSERT INTO message (id, family_id, sender_id, content, message_type, created_at) 
VALUES 
  (1, 1, 1, 'This is a test message for engagement tracking.', 'TEXT', CURRENT_TIMESTAMP),
  (2, 1, 101, 'Another test message.', 'TEXT', CURRENT_TIMESTAMP);

-- Ensure trigger function exists for message preferences
CREATE OR REPLACE FUNCTION create_default_member_preferences()
RETURNS TRIGGER AS $$
BEGIN
    -- For each member in the family, create a preference for the new member
    INSERT INTO user_member_message_settings (user_id, family_id, member_user_id, receive_messages)
    SELECT NEW.user_id, NEW.family_id, member.user_id, true
    FROM user_family_membership member
    WHERE member.family_id = NEW.family_id
    AND NOT EXISTS (
        SELECT 1 FROM user_member_message_settings
        WHERE user_id = NEW.user_id 
        AND family_id = NEW.family_id 
        AND member_user_id = member.user_id
    );
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
CREATE TRIGGER create_member_preferences_on_join
AFTER INSERT ON user_family_membership
FOR EACH ROW
EXECUTE FUNCTION create_default_member_preferences(); 