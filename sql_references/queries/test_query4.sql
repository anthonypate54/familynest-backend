-- Query to check column names in the message table
SELECT column_name
FROM information_schema.columns
WHERE table_name = 'message'
ORDER BY ordinal_position; 