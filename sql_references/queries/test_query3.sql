-- Query focusing just on messages and thumbnail_url
SELECT 
  m.id, 
  m.content, 
  m.sender_id,
  m.family_id,
  m.media_type,
  m.media_url,
  m.thumbnail_url,
  -- Also try with explicit alias to test
  m.thumbnail_url AS "thumbnailUrl"
FROM message m
LIMIT 10; 