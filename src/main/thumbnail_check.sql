-- Query to check which videos have thumbnail_url values
SELECT 
  id, 
  content, 
  media_type, 
  media_url, 
  thumbnail_url,
  timestamp
FROM message 
WHERE 
  media_type = 'video' 
  AND thumbnail_url IS NOT NULL
ORDER BY timestamp DESC
LIMIT 20;

-- Count how many videos have thumbnails vs. total videos
SELECT 
  COUNT(*) as total_videos,
  SUM(CASE WHEN thumbnail_url IS NOT NULL THEN 1 ELSE 0 END) as videos_with_thumbnails,
  ROUND(100.0 * SUM(CASE WHEN thumbnail_url IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as percentage_with_thumbnails
FROM message 
WHERE media_type = 'video'; 