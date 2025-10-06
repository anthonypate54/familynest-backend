-- Check app_user table structure
\d app_user;

-- Check if any subscription-related columns exist in app_user
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'app_user' 
AND (column_name LIKE '%subscription%' OR column_name LIKE '%trial%');
