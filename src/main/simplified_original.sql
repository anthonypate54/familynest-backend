-- Simplified version of the original query
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
), 
message_ids AS (
  SELECT m.id 
  FROM message m 
  WHERE m.family_id IN (SELECT family_id FROM user_families) 
  ORDER BY m.timestamp DESC 
  LIMIT 20
)
SELECT 
  m.id, 
  m.content, 
  m.media_type, 
  m.media_url, 
  m.thumbnail_url
FROM message m 
INNER JOIN message_ids mi ON m.id = mi.id 
ORDER BY m.timestamp DESC; 