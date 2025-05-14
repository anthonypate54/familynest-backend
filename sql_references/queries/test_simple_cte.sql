-- Test simple CTE
WITH sample_messages AS (
  SELECT id, content FROM message LIMIT 3
)
SELECT * FROM sample_messages; 