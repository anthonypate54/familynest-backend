-- Find users who have sent video messages
SELECT DISTINCT m.sender_id, u.username
FROM message m
JOIN app_user u ON m.sender_id = u.id
WHERE m.media_type = 'video'
LIMIT 10;

-- Find which family videos belong to
SELECT m.id, m.content, m.family_id, m.sender_id, m.media_type, m.media_url, m.thumbnail_url
FROM message m
WHERE m.media_type = 'video'
LIMIT 10; 