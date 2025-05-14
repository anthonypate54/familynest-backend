-- Add a unique constraint on created_by in the family table
-- This ensures a user can only create one family
ALTER TABLE family
ADD CONSTRAINT uk_family_creator UNIQUE (created_by);

-- Update roles in user_family_membership table
-- Set ADMIN role for creators of families
UPDATE user_family_membership m
SET role = 'ADMIN'
FROM family f
WHERE m.family_id = f.id AND m.user_id = f.created_by;

-- Set MEMBER role for all other memberships
UPDATE user_family_membership m
SET role = 'MEMBER'
WHERE role IS NULL OR role NOT IN ('ADMIN', 'MEMBER'); 