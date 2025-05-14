-- Check if thumbnail_url column exists and if values are stored
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'message' 
  AND column_name = 'thumbnail_url';

-- Check if any messages have thumbnail_url values
SELECT id, media_type, media_url, thumbnail_url
FROM message
WHERE media_type = 'video'
AND thumbnail_url IS NOT NULL
LIMIT 5; 