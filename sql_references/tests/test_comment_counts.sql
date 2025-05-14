-- Test just the problematic CTE
WITH message_ids AS (
  SELECT id FROM message LIMIT 5
),
comment_counts AS (
  SELECT message_id, COUNT(*) as count 
  FROM message_comment 
  WHERE message_id IN (SELECT id FROM message_ids) 
  GROUP BY message_id
)
SELECT * FROM comment_counts; 