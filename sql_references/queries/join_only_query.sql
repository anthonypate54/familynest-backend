-- Query using only JOINs without any IN clauses
SELECT 
  m.id, 
  m.content, 
  m.sender_username, 
  m.sender_id, 
  m.family_id, 
  m.timestamp, 
  m.media_type, 
  m.media_url, 
  m.thumbnail_url, 
  -- Explicitly create thumbnailUrl field to match what we're doing in code
  m.thumbnail_url AS "thumbnailUrl", 
  u.username, 
  u.first_name, 
  u.last_name, 
  u.photo, 
  -- Calculate counts using LEFT JOINs and GROUP BY
  COALESCE(view_count, 0) as view_count,
  COALESCE(reaction_count, 0) as reaction_count,
  COALESCE(comment_count, 0) as comment_count
FROM message m 
JOIN user_family_membership ufm ON m.family_id = ufm.family_id
LEFT JOIN app_user u ON m.sender_id = u.id 
LEFT JOIN (
  SELECT message_id, COUNT(*) as view_count 
  FROM message_view 
  GROUP BY message_id
) v ON m.id = v.message_id
LEFT JOIN (
  SELECT message_id, COUNT(*) as reaction_count 
  FROM message_reaction 
  GROUP BY message_id
) r ON m.id = r.message_id
LEFT JOIN (
  SELECT message_id, COUNT(*) as comment_count 
  FROM message_comment 
  GROUP BY message_id
) c ON m.id = c.message_id
WHERE ufm.user_id = 1
  AND m.media_type = 'video'
ORDER BY m.timestamp DESC
LIMIT 20; 