-- Complex query similar to what MessageController uses
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
), 
message_ids AS (
  SELECT m.id 
  FROM message m 
  WHERE m.family_id IN (SELECT family_id FROM user_families) 
  ORDER BY m.timestamp DESC 
  LIMIT 20
), 
view_counts AS (
  SELECT message_id, COUNT(*) as count 
  FROM message_view 
  WHERE message_id IN (SELECT id FROM message_ids) 
  GROUP BY message_id
), 
reaction_counts AS (
  SELECT message_id, COUNT(*) as count 
  FROM message_reaction 
  WHERE message_id IN (SELECT id FROM message_ids) 
  GROUP BY message_id
), 
comment_counts AS (
  SELECT message_id, COUNT(*) as count 
  FROM message_comment 
  WHERE message_id IN (SELECT id FROM message_ids) 
  GROUP BY message_id
) 
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
  COALESCE(vc.count, 0) as view_count, 
  COALESCE(rc.count, 0) as reaction_count, 
  COALESCE(cc.count, 0) as comment_count 
FROM message m 
INNER JOIN message_ids mi ON m.id = mi.id 
LEFT JOIN app_user u ON m.sender_id = u.id 
LEFT JOIN view_counts vc ON m.id = vc.message_id 
LEFT JOIN reaction_counts rc ON m.id = rc.message_id 
LEFT JOIN comment_counts cc ON m.id = cc.message_id 
ORDER BY m.timestamp DESC; 