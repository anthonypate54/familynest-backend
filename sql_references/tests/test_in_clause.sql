-- Test simple IN clause
SELECT 
  m.id, 
  m.content, 
  m.family_id
FROM message m
WHERE m.family_id IN (1, 2, 3)
LIMIT 5; 