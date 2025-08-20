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
import org.springframework.beans.factory.annotation.Value;
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
    
    @Autowired
    private VideoController videoController;
    
    @Value("${server.servlet.context-path:}")
    private String contextPath;
    
    @Value("${spring.server.address:localhost}")
    private String serverAddress;
    
    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.default.thumbnail}")
    private String defaultThumbnail;

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
            // 1. Uses JOINs instead of IN clauses (which can be problematic in some SQL engines)
            // 2. Uses efficient joins with explicit join conditions
            // 3. Selects only the fields we need
            // 4. Uses derived tables for count metrics
            // 5. Uses pagination directly in the database
            // 6. Explicitly excludes sensitive data like passwords
            String sql = "SELECT " +
                         "  m.id, m.content, m.sender_username, m.sender_id, m.family_id, " +
                         "  m.timestamp, m.media_type, m.media_url, " +
                         "  m.thumbnail_url, " +
                         "  m.thumbnail_url AS \"thumbnailUrl\", " +
                         "  u.username, u.first_name, u.last_name, u.photo, " +

                         "  COALESCE(reaction_count, 0) as reaction_count, " +
                         "  COALESCE(comment_count, 0) as comment_count " +
                         "FROM message m " +
                         "JOIN user_family_membership ufm ON m.family_id = ufm.family_id " +
                         "LEFT JOIN app_user u ON m.sender_id = u.id " +
                         "-- Removed message_view table references " +
                         "LEFT JOIN ( " +
                         "  SELECT message_id, COUNT(*) as reaction_count " +
                         "  FROM message_reaction " +
                         "  GROUP BY message_id " +
                         ") r ON m.id = r.message_id " +
                         "LEFT JOIN ( " +
                         "  SELECT message_id, COUNT(*) as comment_count " +
                         "  FROM message_comment " +
                         "  GROUP BY message_id " +
                         ") c ON m.id = c.message_id " +
                         "WHERE ufm.user_id = ? " +
                         "ORDER BY m.timestamp ASC " +
                         "LIMIT ? OFFSET ?";
            
            // Run EXPLAIN ANALYZE to help with query optimization (in dev mode only)
            if (logger.isDebugEnabled()) {
                logger.debug("SQL query: {}", sql);
                try {
                    String explainSql = "EXPLAIN ANALYZE " + sql;
                    List<Map<String, Object>> explain = jdbcTemplate.queryForList(explainSql, userId, pageSize, offset);
                    logger.debug("Query plan: {}", explain);
                } catch (Exception e) {
                    logger.error("Error explaining query: {}", e.getMessage());
                }
            }
            
            // Execute the optimized query
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                sql, 
                userId,
                pageSize, 
                offset
            );
            
            // Determine the base URL for media files
             if (!serverAddress.equals("localhost")) {
                baseUrl = "http://" + serverAddress + ":" + serverPort;
            }
            if (!contextPath.isEmpty()) {
                baseUrl = baseUrl + contextPath;
            }
            logger.debug("Using base URL for media: {}", baseUrl);
            
            // Process messages to add full URLs for media
            for (Map<String, Object> message : messages) {
                // Handle media URLs
                if (message.containsKey("media_url") && message.get("media_url") != null) {
                    String mediaUrl = (String) message.get("media_url");
                    if (mediaUrl.startsWith("/")) {
                        // Convert relative URLs to absolute URLs
                        mediaUrl = baseUrl + mediaUrl;
                        message.put("media_url", mediaUrl);
                    }
                    // Add camelCase version
                    message.put("mediaUrl", mediaUrl);
                }
                
                // Handle thumbnail URLs - CRITICAL FIX for Flutter compatibility
                if (message.containsKey("thumbnail_url")) {
                    Object thumbnailValue = message.get("thumbnail_url");
                    if (thumbnailValue != null) {
                        String thumbnailUrl = thumbnailValue.toString();
                        // Make absolute URL if it's relative
                        if (thumbnailUrl.startsWith("/")) {
                            thumbnailUrl = baseUrl + thumbnailUrl;
                            // Update snake_case version too
                            message.put("thumbnail_url", thumbnailUrl);
                        }
                        // Add camelCase version
                        message.put("thumbnailUrl", thumbnailUrl);
                        logger.debug("Added thumbnailUrl: {}", thumbnailUrl);
                    } else {
                        // Null thumbnail but explicitly add the field with null
                        message.put("thumbnailUrl", null);
                    }
                } 
                // Handle videos without thumbnail_url set
                else if ("video".equals(message.get("media_type"))) {
                    logger.debug("Video without thumbnail_url, getting from VideoController");
                    
                    // Use VideoController to get the thumbnail URL instead of generating it here
                    String mediaUrl = message.get("media_url") != null ? message.get("media_url").toString() : null;
                    if (mediaUrl != null) {
                        // Centralized handling via VideoController
                        String thumbnailUrl = videoController.getThumbnailForVideo(mediaUrl);
                        
                        // Make absolute URL if it's relative
                        if (thumbnailUrl != null && thumbnailUrl.startsWith("/")) {
                            thumbnailUrl = baseUrl + thumbnailUrl;
                        }
                        
                        // Add both versions of the thumbnail URL
                        message.put("thumbnail_url", thumbnailUrl);
                        message.put("thumbnailUrl", thumbnailUrl);
                        logger.debug("Got thumbnailUrl from VideoController: {}", thumbnailUrl);
                    } else {
                        // No media URL, use default thumbnail
                        String defThumbnail = baseUrl + defaultThumbnail;
                        message.put("thumbnail_url", defThumbnail);
                        message.put("thumbnailUrl", defThumbnail);
                        logger.debug("Using default thumbnailUrl (no media_url)");
                    }
                }
                
                // Ensure mediaType is present in camelCase
                if (message.containsKey("media_type")) {
                    message.put("mediaType", message.get("media_type"));
                }
                
                // Handle user photo URLs
                if (message.containsKey("photo") && message.get("photo") != null) {
                    String photoUrl = (String) message.get("photo");
                    if (photoUrl.startsWith("/")) {
                        // Convert relative URLs to absolute URLs for user photos
                        message.put("photo", baseUrl + photoUrl);
                    }
                }
            }
            
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
            
            // Debug: Log all field names and check for thumbnail_url and thumbnailUrl fields
            if (logger.isDebugEnabled() && !messages.isEmpty()) {
                logger.debug("First message columns: {}", messages.get(0).keySet());
                logger.debug("First message has thumbnail_url? {}", messages.get(0).containsKey("thumbnail_url"));
                logger.debug("First message has thumbnailUrl? {}", messages.get(0).containsKey("thumbnailUrl"));
                
                // Check case sensitivity variations
                for (String key : messages.get(0).keySet()) {
                    if (key.toLowerCase().contains("thumbnail")) {
                        logger.debug("Found thumbnail-related field: {} with value: {}", key, messages.get(0).get(key));
                    }
                }
                
                // Check first few video messages
                int checkedCount = 0;
                for (Map<String, Object> message : messages) {
                    if ("video".equals(message.get("media_type")) && checkedCount < 3) {
                        logger.debug("Video message ID {}: has thumbnailUrl? {}, value: {}", 
                                    message.get("id"),
                                    message.containsKey("thumbnailUrl"),
                                    message.get("thumbnailUrl"));
                        checkedCount++;
                    }
                }
                
                // Log the final JSON structure
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String jsonResult = mapper.writeValueAsString(result);
                    logger.debug("Final JSON response (first 500 chars): {}", 
                                jsonResult.length() > 500 ? jsonResult.substring(0, 500) + "..." : jsonResult);
                } catch (Exception e) {
                    logger.error("Error serializing response to JSON: {}", e.getMessage());
                }
            }
            
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