-- Check if any videos exist at all
SELECT COUNT(*) as total_videos
FROM message
WHERE media_type = 'video'; 