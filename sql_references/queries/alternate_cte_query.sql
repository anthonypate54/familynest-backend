-- Alternative CTE approach
WITH user_families AS (
  SELECT family_id 
  FROM user_family_membership 
  WHERE user_id = 1
),
filtered_messages AS (
  SELECT 
    m.id,
    m.content, 
    m.sender_username, 
    m.sender_id, 
    m.family_id, 
    m.timestamp, 
    m.media_type, 
    m.media_url, 
    m.thumbnail_url
  FROM message m
  WHERE EXISTS (
    SELECT 1 FROM user_families uf WHERE m.family_id = uf.family_id
  )
  ORDER BY m.timestamp DESC
  LIMIT 20
)
SELECT 
  fm.*,
  u.username, 
  u.first_name, 
  u.last_name, 
  u.photo
FROM filtered_messages fm
LEFT JOIN app_user u ON fm.sender_id = u.id; 