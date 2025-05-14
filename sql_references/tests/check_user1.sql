-- Check if user 1 exists
SELECT id, username FROM app_user WHERE id = 1;

-- Check if user 1's family memberships
SELECT * FROM user_family_membership WHERE user_id = 1; 