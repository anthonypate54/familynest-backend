-- Very simple query that should definitely work
SELECT 
  m.id, 
  m.content, 
  m.media_type, 
  m.media_url, 
  m.thumbnail_url
FROM message m 
WHERE m.media_type = 'video'
ORDER BY m.timestamp DESC
LIMIT 20; 