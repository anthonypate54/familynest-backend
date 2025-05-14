-- Optimized query for loading messages with engagement metrics
-- Replace ? with actual values when testing in dBeaver
-- First parameter: user_id (e.g., 1)
-- Second parameter: limit (e.g., 20)
-- Third parameter: offset (e.g., 0)

-- Version 1: Current query with subqueries (slower)
EXPLAIN ANALYZE
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
)
SELECT m.id, m.content, m.sender_username, m.sender_id, m.family_id,
       m.timestamp, m.media_type, m.media_url,
       u.username, u.first_name, u.last_name, u.photo,
       (SELECT COUNT(*) FROM message_view mv WHERE mv.message_id = m.id) as view_count,
       (SELECT COUNT(*) FROM message_reaction mr WHERE mr.message_id = m.id) as reaction_count,
       (SELECT COUNT(*) FROM message_comment mc WHERE mc.message_id = m.id) as comment_count
FROM message m
LEFT JOIN app_user u ON m.sender_id = u.id
WHERE m.family_id IN (SELECT family_id FROM user_families)
ORDER BY m.timestamp DESC
LIMIT 20 OFFSET 0;

-- Version 2: Optimized query with pre-aggregated counts (faster)
EXPLAIN ANALYZE
WITH user_families AS (
  SELECT family_id FROM user_family_membership WHERE user_id = 1
),
message_ids AS (
  SELECT m.id
  FROM message m
  WHERE m.family_id IN (SELECT family_id FROM user_families)
  ORDER BY m.timestamp DESC
  LIMIT 20 OFFSET 0
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
  m.id, m.content, m.sender_username, m.sender_id, m.family_id,
  m.timestamp, m.media_type, m.media_url,
  u.username, u.first_name, u.last_name, u.photo,
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

-- Version 3: Most optimized query with single-stage fetch and window functions
-- This is more advanced but can be much faster
EXPLAIN ANALYZE
WITH message_data AS (
  SELECT 
    m.id, m.content, m.sender_username, m.sender_id, m.family_id,
    m.timestamp, m.media_type, m.media_url,
    u.username, u.first_name, u.last_name, u.photo
  FROM message m
  LEFT JOIN app_user u ON m.sender_id = u.id
  WHERE m.family_id IN (
    SELECT family_id FROM user_family_membership WHERE user_id = 1
  )
  ORDER BY m.timestamp DESC
  LIMIT 20 OFFSET 0
),
engagement_counts AS (
  SELECT 
    'view' as type, message_id, COUNT(*) as count
  FROM message_view
  WHERE message_id IN (SELECT id FROM message_data)
  GROUP BY message_id
  UNION ALL
  SELECT 
    'reaction' as type, message_id, COUNT(*) as count
  FROM message_reaction
  WHERE message_id IN (SELECT id FROM message_data)
  GROUP BY message_id
  UNION ALL
  SELECT 
    'comment' as type, message_id, COUNT(*) as count
  FROM message_comment
  WHERE message_id IN (SELECT id FROM message_data)
  GROUP BY message_id
),
counts_pivoted AS (
  SELECT
    message_id,
    SUM(CASE WHEN type = 'view' THEN count ELSE 0 END) as view_count,
    SUM(CASE WHEN type = 'reaction' THEN count ELSE 0 END) as reaction_count,
    SUM(CASE WHEN type = 'comment' THEN count ELSE 0 END) as comment_count
  FROM engagement_counts
  GROUP BY message_id
)
SELECT 
  md.*,
  COALESCE(cp.view_count, 0) as view_count,
  COALESCE(cp.reaction_count, 0) as reaction_count,
  COALESCE(cp.comment_count, 0) as comment_count
FROM message_data md
LEFT JOIN counts_pivoted cp ON md.id = cp.message_id
ORDER BY md.timestamp DESC;

-- Version 4: Adding indexes to support the optimized queries
-- Run these index creation statements before testing if they don't exist

-- Index for finding messages by family
CREATE INDEX IF NOT EXISTS idx_message_family_id ON message(family_id);

-- Index for sorting messages by timestamp  
CREATE INDEX IF NOT EXISTS idx_message_timestamp ON message(timestamp DESC);

-- Composite index for family_id and timestamp for the common query pattern
CREATE INDEX IF NOT EXISTS idx_message_family_timestamp ON message(family_id, timestamp DESC);

-- Indexes for engagement tables
CREATE INDEX IF NOT EXISTS idx_message_view_message_id ON message_view(message_id);
CREATE INDEX IF NOT EXISTS idx_message_reaction_message_id ON message_reaction(message_id);
CREATE INDEX IF NOT EXISTS idx_message_comment_message_id ON message_comment(message_id);

-- Index for finding family memberships
CREATE INDEX IF NOT EXISTS idx_user_family_membership_user_id ON user_family_membership(user_id);

-- Analyze all tables to update statistics
ANALYZE message;
ANALYZE message_view;
ANALYZE message_reaction;
ANALYZE message_comment;
ANALYZE app_user;
ANALYZE user_family_membership; 