-- Add onboarding state tracking using bitmap approach
-- Bit 0 (1): Has messages
-- Bit 1 (2): Has DMs  
-- Bit 2 (4): Has family membership
-- Bit 3 (8): Has pending invitations

-- Add the onboarding_state column to app_user table
ALTER TABLE app_user ADD COLUMN onboarding_state INTEGER DEFAULT 0;

-- Create index for efficient onboarding state queries
CREATE INDEX idx_app_user_onboarding_state ON app_user(onboarding_state);

-- Helper function to set a bit in onboarding_state
CREATE OR REPLACE FUNCTION set_onboarding_bit(user_id BIGINT, bit_value INTEGER)
RETURNS void AS $$
BEGIN
    UPDATE app_user 
    SET onboarding_state = onboarding_state | bit_value
    WHERE id = user_id;
END;
$$ LANGUAGE plpgsql;

-- Helper function to clear a bit in onboarding_state
CREATE OR REPLACE FUNCTION clear_onboarding_bit(user_id BIGINT, bit_value INTEGER)
RETURNS void AS $$
BEGIN
    UPDATE app_user 
    SET onboarding_state = onboarding_state & ~bit_value
    WHERE id = user_id;
END;
$$ LANGUAGE plpgsql;

-- TRIGGER 1: Set bit 0 (has messages) when user sends first message
CREATE OR REPLACE FUNCTION trigger_message_onboarding_state()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_onboarding_bit(NEW.sender_id, 1);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER message_onboarding_state_trigger
    AFTER INSERT ON message
    FOR EACH ROW
    EXECUTE FUNCTION trigger_message_onboarding_state();

-- TRIGGER 2: Set bit 1 (has DMs) when user sends first DM
CREATE OR REPLACE FUNCTION trigger_dm_onboarding_state()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_onboarding_bit(NEW.sender_id, 2);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dm_onboarding_state_trigger
    AFTER INSERT ON dm_message
    FOR EACH ROW
    EXECUTE FUNCTION trigger_dm_onboarding_state();

-- TRIGGER 3: Set bit 2 (has family membership) when user creates family
CREATE OR REPLACE FUNCTION trigger_family_creation_onboarding_state()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_onboarding_bit(NEW.created_by, 4);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER family_creation_onboarding_state_trigger
    AFTER INSERT ON family
    FOR EACH ROW
    EXECUTE FUNCTION trigger_family_creation_onboarding_state();

-- TRIGGER 4: Set bit 2 (has family membership) when user joins family
CREATE OR REPLACE FUNCTION trigger_family_membership_onboarding_state()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_onboarding_bit(NEW.user_id, 4);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER family_membership_onboarding_state_trigger
    AFTER INSERT ON user_family_membership
    FOR EACH ROW
    EXECUTE FUNCTION trigger_family_membership_onboarding_state();

-- TRIGGER 5: Set bit 3 (has pending invitations) when invitation is created
CREATE OR REPLACE FUNCTION trigger_invitation_created_onboarding_state()
RETURNS TRIGGER AS $$
DECLARE
    recipient_user_id BIGINT;
BEGIN
    -- Find the user_id for the email (if they exist in our system)
    SELECT id INTO recipient_user_id FROM app_user WHERE email = NEW.email;
    
    -- If user exists, set the pending invitation bit
    IF recipient_user_id IS NOT NULL THEN
        PERFORM set_onboarding_bit(recipient_user_id, 8);
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invitation_created_onboarding_state_trigger
    AFTER INSERT ON invitation
    FOR EACH ROW
    EXECUTE FUNCTION trigger_invitation_created_onboarding_state();

-- TRIGGER 6: Handle invitation status changes (accept/decline)
CREATE OR REPLACE FUNCTION trigger_invitation_status_onboarding_state()
RETURNS TRIGGER AS $$
DECLARE
    recipient_user_id BIGINT;
BEGIN
    -- Find the user_id for the email
    SELECT id INTO recipient_user_id FROM app_user WHERE email = NEW.email;
    
    IF recipient_user_id IS NOT NULL THEN
        -- If invitation was accepted, clear pending bit and set family membership bit
        IF NEW.status = 'ACCEPTED' THEN
            PERFORM clear_onboarding_bit(recipient_user_id, 8);  -- Clear pending invitations
            PERFORM set_onboarding_bit(recipient_user_id, 4);    -- Set family membership
        -- If invitation was declined, just clear the pending bit
        ELSIF NEW.status = 'DECLINED' THEN
            PERFORM clear_onboarding_bit(recipient_user_id, 8);  -- Clear pending invitations
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invitation_status_onboarding_state_trigger
    AFTER UPDATE ON invitation
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION trigger_invitation_status_onboarding_state();

-- Initialize onboarding_state for existing users
-- This will set the appropriate bits for users who already have content

-- Set bit 0 for users who have sent messages
UPDATE app_user 
SET onboarding_state = onboarding_state | 1
WHERE id IN (SELECT DISTINCT sender_id FROM message WHERE sender_id IS NOT NULL);

-- Set bit 1 for users who have sent DMs
UPDATE app_user 
SET onboarding_state = onboarding_state | 2
WHERE id IN (SELECT DISTINCT sender_id FROM dm_message WHERE sender_id IS NOT NULL);

-- Set bit 2 for users who have created families
UPDATE app_user 
SET onboarding_state = onboarding_state | 4
WHERE id IN (SELECT DISTINCT created_by FROM family WHERE created_by IS NOT NULL);

-- Set bit 2 for users who are family members
UPDATE app_user 
SET onboarding_state = onboarding_state | 4
WHERE id IN (SELECT DISTINCT user_id FROM user_family_membership);

-- Set bit 3 for users who have pending invitations
UPDATE app_user 
SET onboarding_state = onboarding_state | 8
WHERE id IN (
    SELECT DISTINCT au.id 
    FROM app_user au 
    JOIN invitation i ON au.email = i.email 
    WHERE i.status = 'PENDING'
);

-- Add helpful comments for future reference
COMMENT ON COLUMN app_user.onboarding_state IS 'Bitmap: bit 0=has messages, bit 1=has DMs, bit 2=has family membership, bit 3=has pending invitations';
COMMENT ON FUNCTION set_onboarding_bit(BIGINT, INTEGER) IS 'Sets a specific bit in user onboarding_state using bitwise OR';
COMMENT ON FUNCTION clear_onboarding_bit(BIGINT, INTEGER) IS 'Clears a specific bit in user onboarding_state using bitwise AND NOT'; 