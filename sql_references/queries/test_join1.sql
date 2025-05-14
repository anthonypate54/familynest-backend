-- Test simple join between message and app_user
SELECT 
  m.id, 
  m.content, 
  u.id as user_id,
  u.username
FROM message m
LEFT JOIN app_user u ON m.sender_id = u.id
LIMIT 5; 