-- Optimized queries for batch loading engagement data
-- For use with the batch engagement endpoint

-- Version 1: Current implementation with separate queries per message
-- This is inefficient as it makes 3 queries per message
-- Replace ? with actual message ID (e.g., 1)
SELECT COUNT(*) FROM message_view WHERE message_id = ?;
SELECT COUNT(*) FROM message_reaction WHERE message_id = ?;
SELECT COUNT(*) FROM message_comment WHERE message_id = ?;

-- Version 2: Optimized batch query for engagement data
-- This loads engagement data for multiple messages in a single query
-- Replace each ? with an actual message ID 
-- First parameter: array of message IDs [1,2,3]
-- Second parameter: user ID (e.g., 1)

WITH message_ids AS (
  SELECT UNNEST(ARRAY[1,2,3]) AS id
),
view_counts AS (
  SELECT message_id, COUNT(*) as count 
  FROM message_view 
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id
),
reaction_counts AS (
  SELECT message_id, reaction_type, COUNT(*) as count
  FROM message_reaction
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id, reaction_type
),
comment_counts AS (
  SELECT message_id, COUNT(*) as count
  FROM message_comment
  WHERE message_id IN (SELECT id FROM message_ids)
  GROUP BY message_id
),
share_counts AS (
  SELECT original_message_id as message_id, COUNT(*) as count
  FROM message_share
  WHERE original_message_id IN (SELECT id FROM message_ids)
  GROUP BY original_message_id
),
user_views AS (
  SELECT message_id, TRUE as viewed
  FROM message_view
  WHERE message_id IN (SELECT id FROM message_ids) AND user_id = 1
)
SELECT 
  mi.id as message_id, 
  COALESCE(vc.count, 0) as view_count,
  COALESCE(cc.count, 0) as comment_count,
  COALESCE(sc.count, 0) as share_count,
  COALESCE(uv.viewed, FALSE) as viewed,
  rc.reaction_type,
  COALESCE(rc.count, 0) as reaction_count
FROM message_ids mi
LEFT JOIN view_counts vc ON mi.id = vc.message_id
LEFT JOIN comment_counts cc ON mi.id = cc.message_id
LEFT JOIN share_counts sc ON mi.id = sc.message_id
LEFT JOIN reaction_counts rc ON mi.id = rc.message_id
LEFT JOIN user_views uv ON mi.id = uv.message_id;

-- Version 3: Most optimized batch query using materialized engagement statistics
-- For implementing this approach, a new table would need to be created:

/*
-- Example schema for a materialized engagement stats table
CREATE TABLE message_engagement_stats (
    message_id BIGINT PRIMARY KEY,
    view_count INTEGER NOT NULL DEFAULT 0,
    reaction_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    share_count INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE
);

-- Example schema for user-specific engagement tracking
CREATE TABLE user_message_engagement (
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    has_viewed BOOLEAN NOT NULL DEFAULT FALSE,
    has_reacted BOOLEAN NOT NULL DEFAULT FALSE,
    has_commented BOOLEAN NOT NULL DEFAULT FALSE,
    has_shared BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, message_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE
);

-- With these tables, the query would be much simpler:
*/

-- Example of query using materialized stats (commented out as tables don't exist yet)
/*
SELECT 
  mi.id as message_id,
  COALESCE(mes.view_count, 0) as view_count,
  COALESCE(mes.comment_count, 0) as comment_count,
  COALESCE(mes.share_count, 0) as share_count,
  COALESCE(ume.has_viewed, FALSE) as viewed,
  -- Additional reaction details would come from a separate query
FROM message_ids mi
LEFT JOIN message_engagement_stats mes ON mi.id = mes.message_id
LEFT JOIN user_message_engagement ume ON mi.id = ume.message_id AND ume.user_id = ?
*/ 