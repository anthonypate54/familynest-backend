# Performance Optimization Implementation Plan

## Current Issues
1. Too many queries being executed when loading messages
2. Subqueries for engagement metrics create N+1 query problem
3. Inefficient loading of engagement data

## Optimization Plan

### Step 1: Add Necessary Indexes
Run the `performance_indexes.sql` script to add all necessary indexes to improve query performance.

### Step 2: Optimize MessageController.java
Replace the current query in `getMessagesForUser` method with the optimized Version 2 query from `optimized_message_query.sql`:

```java
String sql = "WITH user_families AS (" +
            "  SELECT family_id FROM user_family_membership WHERE user_id = ?" +
            "), " +
            "message_ids AS (" +
            "  SELECT m.id " +
            "  FROM message m " +
            "  WHERE m.family_id IN (SELECT family_id FROM user_families) " +
            "  ORDER BY m.timestamp DESC " +
            "  LIMIT ? OFFSET ?" +
            "), " +
            "view_counts AS (" +
            "  SELECT message_id, COUNT(*) as count " +
            "  FROM message_view " +
            "  WHERE message_id IN (SELECT id FROM message_ids) " +
            "  GROUP BY message_id " +
            "), " +
            "reaction_counts AS (" +
            "  SELECT message_id, COUNT(*) as count " +
            "  FROM message_reaction " +
            "  WHERE message_id IN (SELECT id FROM message_ids) " +
            "  GROUP BY message_id " +
            "), " +
            "comment_counts AS (" +
            "  SELECT message_id, COUNT(*) as count " +
            "  FROM message_comment " +
            "  WHERE message_id IN (SELECT id FROM message_ids) " +
            "  GROUP BY message_id " +
            ") " +
            "SELECT " +
            "  m.id, m.content, m.sender_username, m.sender_id, m.family_id, " +
            "  m.timestamp, m.media_type, m.media_url, " +
            "  u.username, u.first_name, u.last_name, u.photo, " +
            "  COALESCE(vc.count, 0) as view_count, " +
            "  COALESCE(rc.count, 0) as reaction_count, " +
            "  COALESCE(cc.count, 0) as comment_count " +
            "FROM message m " +
            "INNER JOIN message_ids mi ON m.id = mi.id " +
            "LEFT JOIN app_user u ON m.sender_id = u.id " +
            "LEFT JOIN view_counts vc ON m.id = vc.message_id " +
            "LEFT JOIN reaction_counts rc ON m.id = rc.message_id " +
            "LEFT JOIN comment_counts cc ON m.id = cc.message_id " +
            "ORDER BY m.timestamp DESC";
```

### Step 3: Optimize UserController.java
Replace the current query in `getMessagesForUser` method with the optimized Version 2 query from `optimized_user_messages_query.sql`.

### Step 4: Implement Batch Loading for Engagement Data
Update the `ViewTrackingController.java` to use the optimized batch query from `batch_engagement_queries.sql`.

### Step 5: Add Cache Headers for Better Client Caching
Make sure appropriate cache headers are added to reduce unnecessary requests:

```java
return ResponseEntity.ok()
    .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
    .body(result);
```

### Step 6: Measure and Verify Improvements
After implementing these changes:
1. Use SQL profiling/monitoring to confirm reduction in query count
2. Compare response times before and after optimization
3. Verify correct data is still being returned
4. Test with larger datasets to ensure scalability

### Future Optimizations (if needed)
1. Implement materialized engagement statistics as shown in Version 3 of `batch_engagement_queries.sql`
2. Add database-level function to calculate engagement metrics in a single call
3. Implement reactive/asynchronous loading for non-blocking performance 