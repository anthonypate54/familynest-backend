-- V27__Add_message_preferences_trigger.sql
-- Migration to ensure member message preferences exist for all family members

-- First, create a function to populate member message preferences
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

-- Create a trigger to run this function whenever a user joins a family
DROP TRIGGER IF EXISTS create_member_preferences_on_join ON user_family_membership;
CREATE TRIGGER create_member_preferences_on_join
AFTER INSERT ON user_family_membership
FOR EACH ROW
EXECUTE FUNCTION create_default_member_preferences();

-- Populate missing preferences for existing family memberships
INSERT INTO user_member_message_settings (user_id, family_id, member_user_id, receive_messages)
SELECT 
    user_memb.user_id, 
    user_memb.family_id, 
    family_memb.user_id,
    true
FROM 
    user_family_membership user_memb
CROSS JOIN 
    user_family_membership family_memb
WHERE 
    user_memb.family_id = family_memb.family_id
    AND NOT EXISTS (
        SELECT 1 
        FROM user_member_message_settings 
        WHERE user_id = user_memb.user_id 
        AND family_id = user_memb.family_id 
        AND member_user_id = family_memb.user_id
    );
