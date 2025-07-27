package com.familynest.controller;

import com.familynest.model.MessageView;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class ViewTrackingController {

    private static final Logger logger = LoggerFactory.getLogger(ViewTrackingController.class);

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/{messageId}/views")
    public ResponseEntity<Map<String, Object>> markMessageAsViewed(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Marking message ID: {} as viewed", messageId);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            MessageView view = engagementService.markMessageAsViewed(messageId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", view.getId());
            response.put("messageId", view.getMessageId());
            response.put("userId", view.getUserId());
            response.put("viewedAt", view.getViewedAt());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error marking message as viewed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error marking message as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark message as viewed: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/views")
    public ResponseEntity<Map<String, Object>> getMessageViews(@PathVariable Long messageId) {
        logger.debug("Getting views for message ID: {}", messageId);
        
        try {
            List<MessageView> views = engagementService.getMessageViews(messageId);
            long viewCount = views.size();
            
            // Get user details for each view
            List<Map<String, Object>> viewDetails = new ArrayList<>();
            for (MessageView view : views) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", view.getId());
                detail.put("userId", view.getUserId());
                detail.put("viewedAt", view.getViewedAt());
                
                // Add basic user info
                User user = userRepository.findById(view.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                viewDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("views", viewDetails);
            response.put("viewCount", viewCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting message views: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get message views: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/views/check")
    public ResponseEntity<Map<String, Object>> checkIfMessageViewed(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Checking if message ID: {} has been viewed", messageId);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            boolean isViewed = engagementService.isMessageViewedByUser(messageId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("viewed", isViewed);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking if message viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check if message viewed: " + e.getMessage()));
        }
    }

    @PostMapping("/batch-views")
    public ResponseEntity<Map<String, Object>> markMultipleMessagesAsViewed(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Batch marking messages as viewed");
        
        try {
            // Extract message IDs from request body
            @SuppressWarnings("unchecked")
            List<Object> messageIdObjects = (List<Object>) requestBody.get("messageIds");
            
            if (messageIdObjects == null || messageIdObjects.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "messageIds array is required and cannot be empty"));
            }
            
            if (messageIdObjects.size() > 50) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot mark more than 50 messages at once"));
            }
            
            // Convert to Long list
            List<Long> messageIds = messageIdObjects.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .toList();
            
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            // Mark each message as viewed
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int skippedCount = 0;
            
            for (Long messageId : messageIds) {
                try {
                    // Check if already viewed to avoid duplicates
                    boolean alreadyViewed = engagementService.isMessageViewedByUser(messageId, userId);
                    if (alreadyViewed) {
                        skippedCount++;
                        continue;
                    }
                    
                    MessageView view = engagementService.markMessageAsViewed(messageId, userId);
                    Map<String, Object> viewData = new HashMap<>();
                    viewData.put("messageId", view.getMessageId());
                    viewData.put("userId", view.getUserId());
                    viewData.put("viewedAt", view.getViewedAt());
                    results.add(viewData);
                    successCount++;
                    
                } catch (Exception e) {
                    logger.warn("Failed to mark message {} as viewed: {}", messageId, e.getMessage());
                    // Continue processing other messages
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("successCount", successCount);
            response.put("skippedCount", skippedCount);
            response.put("totalRequested", messageIds.size());
            
            logger.debug("Batch view marking completed: {} success, {} skipped out of {} requested", 
                    successCount, skippedCount, messageIds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error in batch marking messages as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to batch mark messages as viewed: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/engagement")
    public ResponseEntity<Map<String, Object>> getMessageEngagementData(
            @PathVariable Long messageId,
            HttpServletRequest request) {
        logger.debug("Getting engagement data for message ID: {}", messageId);
        
        try {
            // For testing, add the viewed status based on the current test user
            Map<String, Object> engagementData = engagementService.getMessageEngagementData(messageId);
            
            // If in test environment, check if the test user has viewed the message
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                Long userId = (Long) userIdAttr;
                boolean isViewed = engagementService.isMessageViewedByUser(messageId, userId);
                engagementData.put("viewed", isViewed);
                logger.debug("Added viewed status for test user: {} = {}", userId, isViewed);
            }
            
            return ResponseEntity.ok(engagementData);
        } catch (Exception e) {
            logger.error("Error getting message engagement data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get message engagement data: " + e.getMessage()));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadMessageCount(
            @RequestParam(required = false) Long familyId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Getting unread message count for user");
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            String sql;
            List<Object> params = new ArrayList<>();
            
            if (familyId != null) {
                // Get unread count for specific family
                sql = """
                    SELECT COUNT(DISTINCT m.id) as unread_count
                    FROM message m
                    JOIN user_family_membership ufm ON m.family_id = ufm.family_id
                    LEFT JOIN message_view mv ON (m.id = mv.message_id AND mv.user_id = ?)
                    WHERE ufm.user_id = ?
                    AND m.family_id = ?
                    AND m.sender_id != ?
                    AND mv.id IS NULL
                    AND m.timestamp > CURRENT_DATE - INTERVAL '30 days'
                """;
                params.add(userId);
                params.add(userId);
                params.add(familyId);
                params.add(userId);
            } else {
                // Get total unread count across all families
                sql = """
                    SELECT COUNT(DISTINCT m.id) as unread_count
                    FROM message m
                    JOIN user_family_membership ufm ON m.family_id = ufm.family_id
                    LEFT JOIN message_view mv ON (m.id = mv.message_id AND mv.user_id = ?)
                    WHERE ufm.user_id = ?
                    AND m.sender_id != ?
                    AND mv.id IS NULL
                    AND m.timestamp > CURRENT_DATE - INTERVAL '30 days'
                """;
                params.add(userId);
                params.add(userId);
                params.add(userId);
            }
            
            Integer unreadCount = jdbcTemplate.queryForObject(sql, Integer.class, params.toArray());
            
            Map<String, Object> response = new HashMap<>();
            response.put("unreadCount", unreadCount != null ? unreadCount : 0);
            response.put("userId", userId);
            if (familyId != null) {
                response.put("familyId", familyId);
            }
            
            logger.debug("Unread message count for user {}: {}", userId, unreadCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting unread message count: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get unread message count: " + e.getMessage()));
        }
    }

    @GetMapping("/unread-by-family")
    public ResponseEntity<Map<String, Object>> getUnreadMessageCountByFamily(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Getting unread message count by family for user");
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            String sql = """
                SELECT 
                    f.id as family_id,
                    f.name as family_name,
                    COUNT(DISTINCT m.id) as unread_count
                FROM family f
                JOIN user_family_membership ufm ON f.id = ufm.family_id
                LEFT JOIN message m ON f.id = m.family_id 
                    AND m.sender_id != ? 
                    AND m.timestamp > CURRENT_DATE - INTERVAL '30 days'
                LEFT JOIN message_view mv ON (m.id = mv.message_id AND mv.user_id = ?)
                WHERE ufm.user_id = ?
                AND (m.id IS NULL OR mv.id IS NULL)
                GROUP BY f.id, f.name
                ORDER BY unread_count DESC, f.name
            """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, userId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("families", results);
            response.put("userId", userId);
            
            logger.debug("Unread message count by family for user {}: {} families", userId, results.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting unread message count by family: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get unread message count by family: " + e.getMessage()));
        }
    }

    /**
     * Add a new batch endpoint to load engagement data for multiple messages at once
     * This is much more efficient than loading one at a time from the client
     */
    @GetMapping("/batch-engagement")
    public ResponseEntity<Map<String, Object>> getBatchMessageEngagementData(
            @RequestParam List<Long> messageIds,
            HttpServletRequest request) {
        logger.debug("Getting batch engagement data for {} messages", messageIds.size());
        
        if (messageIds.isEmpty() || messageIds.size() > 50) { // Limit batch size to prevent abuse
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid batch size. Must be between 1 and 50 messages."
            ));
        }
        
        try {
            // Get the current user ID for view tracking
            Long userId = (Long) request.getAttribute("userId");
            
            // Create an optimized single query to get engagement data for all messages at once
            String sql = "WITH reaction_counts AS (" +
                        "  SELECT message_id, reaction_type, COUNT(*) as count " +
                        "  FROM message_reaction " +
                        "  WHERE message_id IN (%s) " +
                        "  GROUP BY message_id, reaction_type" +
                        "), " +
                        "view_counts AS (" +
                        "  SELECT message_id, COUNT(*) as count " +
                        "  FROM message_view " + 
                        "  WHERE message_id IN (%s) " +
                        "  GROUP BY message_id" +
                        "), " +
                        "comment_counts AS (" +
                        "  SELECT message_id, COUNT(*) as count " +
                        "  FROM message_comment " +
                        "  WHERE message_id IN (%s) " +
                        "  GROUP BY message_id" +
                        "), " +
                        "share_counts AS (" +
                        "  SELECT original_message_id as message_id, COUNT(*) as count " +
                        "  FROM message_share " +
                        "  WHERE original_message_id IN (%s) " +
                        "  GROUP BY original_message_id" +
                        "), " +
                        "user_views AS (" +
                        "  SELECT message_id, true as viewed " +
                        "  FROM message_view " +
                        "  WHERE message_id IN (%s) AND user_id = ?" +
                        ") " +
                        "SELECT m.id as message_id, " +
                        "  COALESCE(vc.count, 0) as view_count, " +
                        "  COALESCE(cc.count, 0) as comment_count, " +
                        "  COALESCE(sc.count, 0) as share_count, " +
                        "  COALESCE(uv.viewed, false) as viewed, " +
                        "  rc.reaction_type, COALESCE(rc.count, 0) as reaction_count " +
                        "FROM (SELECT DISTINCT id FROM unnest(array[%s]) id) m " +
                        "LEFT JOIN view_counts vc ON m.id = vc.message_id " +
                        "LEFT JOIN comment_counts cc ON m.id = cc.message_id " +
                        "LEFT JOIN share_counts sc ON m.id = sc.message_id " +
                        "LEFT JOIN reaction_counts rc ON m.id = rc.message_id " +
                        "LEFT JOIN user_views uv ON m.id = uv.message_id";
                        
            // Create the placeholders for the IN clauses
            String placeholders = String.join(",", messageIds.stream().map(id -> "?").collect(Collectors.toList()));
            String formattedSql = String.format(sql, 
                placeholders, placeholders, placeholders, placeholders, placeholders, placeholders);
                
            // Create parameters array (messageIds for each IN clause plus userId)
            Object[] params = new Object[messageIds.size() * 6 + 1];
            
            // Fill the parameters array
            int paramIndex = 0;
            
            // For reaction_counts
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            
            // For view_counts
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            
            // For comment_counts
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            
            // For share_counts
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            
            // For user_views
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            params[paramIndex++] = userId;
            
            // For message IDs in the final SELECT
            for (Long messageId : messageIds) {
                params[paramIndex++] = messageId;
            }
            
            logger.debug("Executing batch engagement query for {} messages", messageIds.size());
            List<Map<String, Object>> results = jdbcTemplate.queryForList(formattedSql, params);
            
            // Process the results
            Map<Long, Map<String, Object>> messageEngagementMap = new HashMap<>();
            Map<Long, Map<String, Integer>> reactionsMap = new HashMap<>();
            
            for (Map<String, Object> row : results) {
                Long messageId = ((Number) row.get("message_id")).longValue();
                
                // Initialize message data if not exists
                if (!messageEngagementMap.containsKey(messageId)) {
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("viewCount", row.get("view_count"));
                    messageData.put("commentCount", row.get("comment_count"));
                    messageData.put("shareCount", row.get("share_count"));
                    messageData.put("viewed", row.get("viewed"));
                    messageData.put("reactions", new HashMap<String, Integer>());
                    messageEngagementMap.put(messageId, messageData);
                    reactionsMap.put(messageId, new HashMap<>());
                }
                
                // Add reaction data if present
                if (row.get("reaction_type") != null) {
                    String reactionType = (String) row.get("reaction_type");
                    Integer count = ((Number) row.get("reaction_count")).intValue();
                    reactionsMap.get(messageId).put(reactionType, count);
                }
            }
            
            // Add reactions to each message
            for (Long messageId : messageEngagementMap.keySet()) {
                messageEngagementMap.get(messageId).put("reactions", reactionsMap.get(messageId));
            }
            
            // Create the final response
            Map<String, Object> response = new HashMap<>();
            response.put("messages", messageEngagementMap);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting batch message engagement data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get batch message engagement data: " + e.getMessage()));
        }
    }

    // DM Message View Tracking Endpoints

    @PostMapping("/dm/{dmMessageId}/views")
    public ResponseEntity<Map<String, Object>> markDMMessageAsViewed(
            @PathVariable Long dmMessageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Marking DM message ID: {} as viewed", dmMessageId);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }

            MessageView view = engagementService.markDMMessageAsViewed(dmMessageId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("view", Map.of(
                    "id", view.getId(),
                    "dmMessageId", view.getDmMessageId(),
                    "userId", view.getUserId(),
                    "viewedAt", view.getViewedAt().toString(),
                    "messageType", view.getMessageType()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking DM message as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark DM message as viewed: " + e.getMessage()));
        }
    }

    @PostMapping("/dm/batch-views")
    public ResponseEntity<Map<String, Object>> batchMarkDMMessagesAsViewed(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Batch marking DM messages as viewed: {}", requestBody);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }

            @SuppressWarnings("unchecked")
            List<Integer> dmMessageIds = (List<Integer>) requestBody.get("dmMessageIds");
            if (dmMessageIds == null || dmMessageIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "dmMessageIds is required and cannot be empty"));
            }

            List<MessageView> views = new ArrayList<>();
            for (Integer dmMessageId : dmMessageIds) {
                try {
                    MessageView view = engagementService.markDMMessageAsViewed(dmMessageId.longValue(), userId);
                    views.add(view);
                } catch (Exception e) {
                    logger.warn("Failed to mark DM message {} as viewed: {}", dmMessageId, e.getMessage());
                    // Continue with other messages even if one fails
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("markedCount", views.size());
            response.put("requestedCount", dmMessageIds.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error batch marking DM messages as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to batch mark DM messages as viewed: " + e.getMessage()));
        }
    }

    @GetMapping("/dm/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadDMMessageCount(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Getting unread DM message count");
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }

            // Query to count unread DM messages for this user
            String sql = """
                    SELECT COUNT(DISTINCT dm.id) 
                    FROM dm_message dm
                    JOIN dm_conversation dc ON dm.conversation_id = dc.id
                    WHERE (dc.user1_id = ? OR dc.user2_id = ?)
                    AND dm.sender_id != ?
                    AND NOT EXISTS (
                        SELECT 1 FROM message_view mv 
                        WHERE mv.dm_message_id = dm.id 
                        AND mv.user_id = ? 
                        AND mv.message_type = 'dm'
                    )
                    """;

            Long unreadCount = jdbcTemplate.queryForObject(sql, Long.class, userId, userId, userId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("unreadCount", unreadCount != null ? unreadCount : 0L);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting unread DM message count: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get unread DM message count: " + e.getMessage()));
        }
    }
} 