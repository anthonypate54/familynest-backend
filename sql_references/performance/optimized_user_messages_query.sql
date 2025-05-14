-- Optimized query for loading messages from UserController
-- Replace parameter 1 with the actual user_id (e.g., 1)

-- Version 1: Current query with subqueries (slower)
EXPLAIN ANALYZE
WITH user_check AS (
  SELECT id FROM app_user WHERE id = 1
), 
user_families AS (
  SELECT ufm.family_id 
  FROM user_family_membership ufm 
  WHERE ufm.user_id = 1
), 
muted_families AS (
  SELECT ufms.family_id 
  FROM user_family_message_settings ufms 
  WHERE ufms.user_id = 1 AND ufms.receive_messages = false
), 
active_families AS (
  SELECT uf.family_id 
  FROM user_families uf 
  LEFT JOIN muted_families mf ON uf.family_id = mf.family_id 
  WHERE mf.family_id IS NULL
) 
SELECT 
  m.id, m.content, m.sender_username, m.sender_id, m.family_id, 
  m.timestamp, m.media_type, m.media_url, 
  s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, 
  f.name as family_name, 
  (SELECT COUNT(*) FROM message_view mv WHERE mv.message_id = m.id) as view_count, 
  (SELECT COUNT(*) FROM message_reaction mr WHERE mr.message_id = m.id) as reaction_count, 
  (SELECT COUNT(*) FROM message_comment mc WHERE mc.message_id = m.id) as comment_count 
FROM message m 
JOIN active_families af ON m.family_id = af.family_id 
LEFT JOIN app_user s ON m.sender_id = s.id 
LEFT JOIN family f ON m.family_id = f.id 
ORDER BY m.timestamp DESC 
LIMIT 100;

-- Version 2: Optimized query with pre-filtered messages and pre-aggregated counts
EXPLAIN ANALYZE
WITH user_check AS (
  SELECT id FROM app_user WHERE id = 1
), 
user_families AS (
  SELECT ufm.family_id 
  FROM user_family_membership ufm 
  WHERE ufm.user_id = 1
), 
muted_families AS (
  SELECT ufms.family_id 
  FROM user_family_message_settings ufms 
  WHERE ufms.user_id = 1 AND ufms.receive_messages = false
), 
active_families AS (
  SELECT uf.family_id 
  FROM user_families uf 
  LEFT JOIN muted_families mf ON uf.family_id = mf.family_id 
  WHERE mf.family_id IS NULL
),
message_subset AS (
  SELECT m.id
  FROM message m 
  JOIN active_families af ON m.family_id = af.family_id 
  ORDER BY m.timestamp DESC 
  LIMIT 100
),
view_counts AS (
  SELECT message_id, COUNT(*) as count
  FROM message_view
  WHERE message_id IN (SELECT id FROM message_subset)
  GROUP BY message_id
),
reaction_counts AS (
  SELECT message_id, COUNT(*) as count
  FROM message_reaction
  WHERE message_id IN (SELECT id FROM message_subset)
  GROUP BY message_id
),
comment_counts AS (
  SELECT message_id, COUNT(*) as count
  FROM message_comment
  WHERE message_id IN (SELECT id FROM message_subset)
  GROUP BY message_id
)
SELECT 
  m.id, m.content, m.sender_username, m.sender_id, m.family_id, 
  m.timestamp, m.media_type, m.media_url, 
  s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, 
  f.name as family_name, 
  COALESCE(vc.count, 0) as view_count, 
  COALESCE(rc.count, 0) as reaction_count, 
  COALESCE(cc.count, 0) as comment_count 
FROM message m 
JOIN message_subset ms ON m.id = ms.id
LEFT JOIN app_user s ON m.sender_id = s.id 
LEFT JOIN family f ON m.family_id = f.id 
LEFT JOIN view_counts vc ON m.id = vc.message_id
LEFT JOIN reaction_counts rc ON m.id = rc.message_id
LEFT JOIN comment_counts cc ON m.id = cc.message_id
ORDER BY m.timestamp DESC;

-- Version 3: Further optimized with index hints and more efficient joins
EXPLAIN ANALYZE
WITH RECURSIVE user_active_families AS (
  SELECT DISTINCT ufm.family_id 
  FROM user_family_membership ufm 
  LEFT JOIN user_family_message_settings ufms ON 
    ufm.user_id = ufms.user_id AND 
    ufm.family_id = ufms.family_id AND 
    ufms.receive_messages = false
  WHERE ufm.user_id = 1
    AND ufms.family_id IS NULL -- Only include non-muted families
),
filtered_messages AS (
  SELECT 
    m.id, m.content, m.sender_username, m.sender_id, m.family_id, 
    m.timestamp, m.media_type, m.media_url
  FROM message m 
  JOIN user_active_families uaf ON m.family_id = uaf.family_id
  ORDER BY m.timestamp DESC 
  LIMIT 100
),
message_ids AS (
  SELECT id FROM filtered_messages
),
engagement_data AS (
  -- Views
  SELECT 'view' AS type, message_id, COUNT(*) AS count
  FROM message_view
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id
  
  UNION ALL
  
  -- Reactions
  SELECT 'reaction' AS type, message_id, COUNT(*) AS count
  FROM message_reaction
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id
  
  UNION ALL
  
  -- Comments
  SELECT 'comment' AS type, message_id, COUNT(*) AS count
  FROM message_comment
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id
),
engagement_summary AS (
  SELECT
    message_id,
    SUM(CASE WHEN type = 'view' THEN count ELSE 0 END) AS view_count,
    SUM(CASE WHEN type = 'reaction' THEN count ELSE 0 END) AS reaction_count,
    SUM(CASE WHEN type = 'comment' THEN count ELSE 0 END) AS comment_count
  FROM engagement_data
  GROUP BY message_id
)
SELECT 
  m.id, m.content, m.sender_username, m.sender_id, m.family_id, 
  m.timestamp, m.media_type, m.media_url, 
  u.photo as sender_photo, u.first_name as sender_first_name, u.last_name as sender_last_name, 
  f.name as family_name, 
  COALESCE(es.view_count, 0) as view_count, 
  COALESCE(es.reaction_count, 0) as reaction_count, 
  COALESCE(es.comment_count, 0) as comment_count 
FROM filtered_messages m 
LEFT JOIN app_user u ON m.sender_id = u.id 
LEFT JOIN family f ON m.family_id = f.id 
LEFT JOIN engagement_summary es ON m.id = es.message_id
ORDER BY m.timestamp DESC; 