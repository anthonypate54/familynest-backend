-- Test subquery without IN clause
SELECT 
  m.id, 
  m.content, 
  m.family_id
FROM message m
WHERE m.family_id = (SELECT MIN(family_id) FROM user_family_membership)
LIMIT 5; 