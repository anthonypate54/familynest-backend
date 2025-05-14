-- Fixed complex query without using IN clause with subquery
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
JOIN user_family_membership ufm ON m.family_id = ufm.family_id
LEFT JOIN app_user u ON m.sender_id = u.id 
WHERE ufm.user_id = 1
  AND m.media_type = 'video'
ORDER BY m.timestamp DESC
LIMIT 20; 