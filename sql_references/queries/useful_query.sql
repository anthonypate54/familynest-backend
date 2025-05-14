-- Query to examine video messages and their thumbnails
SELECT 
  id, 
  content, 
  sender_id, 
  family_id,
  media_type, 
  media_url, 
  thumbnail_url
FROM message 
WHERE media_type = 'video'
ORDER BY timestamp DESC
LIMIT 20; 