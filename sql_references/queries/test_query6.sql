-- Find video messages (potential candidates for thumbnails)
SELECT id, content, media_type, media_url, thumbnail_url
FROM message
WHERE media_type = 'video'
LIMIT 10; 