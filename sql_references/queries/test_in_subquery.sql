-- Test simple IN with subquery
SELECT 
  id, 
  content, 
  family_id
FROM message
WHERE family_id IN (SELECT DISTINCT family_id FROM user_family_membership LIMIT 3)
LIMIT 5; 