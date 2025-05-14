-- Test just the first CTE
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
)
SELECT * FROM user_families; 