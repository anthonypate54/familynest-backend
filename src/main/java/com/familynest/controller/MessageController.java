package com.familynest.controller;

import com.familynest.model.Message;
import com.familynest.model.User;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.MessageRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository;

    /**
     * Get messages for a user with pagination - super optimized version using a single efficient SQL query
     * This endpoint is much faster than the previous version because:
     * 1. It uses a single SQL query with appropriate joins
     * 2. It only selects the fields we actually need
     * 3. It uses a CTE (Common Table Expression) to avoid N+1 queries
     * 4. It computes metrics (counts) directly in the database
     * 5. It properly handles pagination at the database level
     * 6. It includes EXPLAIN ANALYZE to help with query tuning
     * 7. It adds response caching for better performance
     * 8. It excludes sensitive data like passwords
     */
    @GetMapping("/user/{userId}")
    @Cacheable(value = "userMessages", key = "#userId + '-' + #page + '-' + #size")
    public ResponseEntity<?> getMessagesForUser(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        
        logger.debug("Getting messages for user: {}, page: {}, size: {}", userId, page, size);
        
        try {
            // Validate page size to prevent loading too much data
            int pageSize = Math.min(size, 50);
            int offset = page * pageSize;
            
            // Verify user exists (lightweight check)
            if (!userRepository.existsById(userId)) {
                logger.debug("User not found: {}", userId);
                return ResponseEntity.notFound().build();
            }
            
            // Get messages with a single optimized SQL query
            // This query:
            // 1. Uses a CTE to limit data fetched (user_families)
            // 2. Uses efficient joins with explicit join conditions
            // 3. Selects only the fields we need
            // 4. Uses subqueries for count metrics
            // 5. Uses pagination directly in the database
            // 6. Explicitly excludes sensitive data like passwords
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
            
            // Run EXPLAIN ANALYZE to help with query optimization (in dev mode only)
            if (logger.isDebugEnabled()) {
                String explainSql = "EXPLAIN ANALYZE " + sql;
                List<Map<String, Object>> explain = jdbcTemplate.queryForList(explainSql, userId, pageSize, offset);
                logger.debug("Query plan: {}", explain);
            }
            
            // Execute the optimized query
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                sql, 
                userId,
                pageSize, 
                offset
            );
            
            // Get the total count with an efficient count query
            String countSql = "SELECT COUNT(*) FROM message m " +
                             "WHERE m.family_id IN (SELECT family_id FROM user_family_membership WHERE user_id = ?)";
            
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, userId);
            boolean hasMore = totalCount != null && (offset + messages.size() < totalCount);
            
            // Create pagination metadata
            Map<String, Object> result = new HashMap<>();
            result.put("messages", messages);
            result.put("totalElements", totalCount);
            result.put("totalPages", (int) Math.ceil((double) totalCount / pageSize));
            result.put("currentPage", page);
            result.put("pageSize", pageSize);
            
            logger.debug("Returning {} messages for user {} (total {})", messages.size(), userId, totalCount);
            
            // Add cache control headers
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(result);
        } catch (Exception e) {
            logger.error("Error getting messages for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error getting messages: " + e.getMessage()));
        }
    }
} 