-- Simplified complex query that should work in PostgreSQL
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
)
SELECT 
  m.id, 
  m.content, 
  m.sender_username, 
  m.sender_id, 
  m.family_id, 
  m.timestamp, 
  m.media_type, 
  m.media_url, 
  m.thumbnail_url,
  u.username, 
  u.first_name, 
  u.last_name, 
  u.photo
FROM message m 
LEFT JOIN app_user u ON m.sender_id = u.id 
WHERE m.family_id IN (SELECT family_id FROM user_families) 
ORDER BY m.timestamp DESC
LIMIT 20; 