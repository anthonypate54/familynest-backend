-- Find tables related to subscriptions
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name LIKE '%subscription%' OR table_name LIKE '%payment%';
