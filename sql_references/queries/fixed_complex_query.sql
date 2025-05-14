-- Fixed complex query addressing the message_id and id references
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
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
  WHERE m.family_id IN (SELECT family_id FROM user_families) 
  ORDER BY m.timestamp DESC 
  LIMIT 20
)
SELECT 
  fm.id, 
  fm.content, 
  fm.sender_username, 
  fm.sender_id, 
  fm.family_id, 
  fm.timestamp, 
  fm.media_type, 
  fm.media_url, 
  fm.thumbnail_url, 
  -- Explicitly create thumbnailUrl field to match what we're doing in code
  fm.thumbnail_url AS "thumbnailUrl", 
  u.username, 
  u.first_name, 
  u.last_name, 
  u.photo, 
  -- Calculate counts using correlated subqueries instead of CTEs
  COALESCE((SELECT COUNT(*) FROM message_view WHERE message_id = fm.id), 0) as view_count,
  COALESCE((SELECT COUNT(*) FROM message_reaction WHERE message_id = fm.id), 0) as reaction_count,
  COALESCE((SELECT COUNT(*) FROM message_comment WHERE message_id = fm.id), 0) as comment_count
FROM filtered_messages fm
LEFT JOIN app_user u ON fm.sender_id = u.id 
ORDER BY fm.timestamp DESC; 