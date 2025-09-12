-- Minimal query to find videos with thumbnails
SELECT 
  m.id, 
  m.content, 
  m.media_type, 
  m.media_url, 
  m.thumbnail_url,
  m.thumbnail_url AS "thumbnailUrl"
FROM message m 
JOIN user_family_membership ufm ON m.family_id = ufm.family_id
WHERE ufm.user_id = 1
  AND m.media_type = 'video'
ORDER BY m.timestamp DESC
LIMIT 20; 