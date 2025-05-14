-- Query to check if thumbnail_url column exists
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'message' 
ORDER BY ordinal_position; 