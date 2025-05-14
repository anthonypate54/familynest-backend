-- Test WHERE clause with user_id
SELECT 
  ufm.family_id
FROM user_family_membership ufm
WHERE ufm.user_id = 1
LIMIT 5; 