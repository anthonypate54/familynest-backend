-- Test with two CTEs
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
),
message_ids AS (
  SELECT m.id 
  FROM message m 
  WHERE m.family_id IN (SELECT family_id FROM user_families) 
  ORDER BY m.timestamp DESC 
  LIMIT 10 OFFSET 0
)
SELECT * FROM message_ids; 