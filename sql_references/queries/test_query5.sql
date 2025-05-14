-- Query with explicit column names without aliases
SELECT id, content, family_id, media_type, media_url, thumbnail_url
FROM message
LIMIT 10; 