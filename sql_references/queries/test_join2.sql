-- Test join between message and user_family_membership
SELECT 
  m.id, 
  m.content, 
  m.family_id,
  ufm.user_id
FROM message m
JOIN user_family_membership ufm ON m.family_id = ufm.family_id
LIMIT 5; 