-- Enforce that a user can only own one family
-- This constraint ensures each user can only be the creator/owner of one family
ALTER TABLE family ADD CONSTRAINT one_family_per_owner UNIQUE (created_by);

-- Add comment to document the constraint
COMMENT ON CONSTRAINT one_family_per_owner ON family IS
'Ensures a user can only own one family';
