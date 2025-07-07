-- Add new member tracking to user_family_membership table
ALTER TABLE user_family_membership 
ADD COLUMN is_new_member BOOLEAN DEFAULT true;

-- Set existing members to false (they're not new anymore)
UPDATE user_family_membership 
SET is_new_member = false;

-- Create index for efficient queries
CREATE INDEX idx_user_family_membership_new_member ON user_family_membership(family_id, is_new_member); 