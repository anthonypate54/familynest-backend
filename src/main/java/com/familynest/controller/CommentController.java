package com.familynest.controller;

import com.familynest.model.MessageComment;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;

@RestController
@RequestMapping("/api/messages")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Get comments for a message with efficient pagination
     * Uses a single optimized SQL query instead of multiple Hibernate queries
     */
    @GetMapping("/{messageId}/comments")
    @Cacheable(value = "messageComments", key = "#messageId + '-' + #page + '-' + #size")
    public ResponseEntity<Map<String, Object>> getComments(
            @PathVariable Long messageId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        
        logger.debug("Getting comments for message: {}, page: {}, size: {}", messageId, page, size);
        
        try {
            // Limit page size to prevent abuse
            int pageSize = Math.min(size, 50);
            int offset = page * pageSize;
            
            // Use a single optimized query to get comments with user data
            String sql = "WITH comment_count AS (" +
                       "  SELECT COUNT(*) as total FROM message_comment WHERE message_id = ?" +
                       ") " +
                       "SELECT mc.id, mc.message_id, mc.user_id, mc.content, mc.media_url, mc.media_type, " +
                       "       mc.created_at, mc.updated_at, mc.parent_comment_id, " +
                       "       u.username, u.first_name, u.last_name, u.photo, " +
                       "       cc.total as total_count " +
                       "FROM message_comment mc " +
                       "JOIN app_user u ON mc.user_id = u.id " +
                       "CROSS JOIN comment_count cc " +
                       "WHERE mc.message_id = ? " +
                       "ORDER BY mc.created_at ASC " +
                       "LIMIT ? OFFSET ?";
            
            // Execute query
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, messageId, messageId, pageSize, offset);
            
            // Check if we got any results
            if (results.isEmpty()) {
                // If no comments, let's at least check if the message exists
                String checkSql = "SELECT EXISTS(SELECT 1 FROM message WHERE id = ?)";
                boolean messageExists = jdbcTemplate.queryForObject(checkSql, Boolean.class, messageId);
                
                if (!messageExists) {
                    logger.debug("Message not found: {}", messageId);
                    return ResponseEntity.notFound().build();
                }
                
                // Message exists but no comments
                return ResponseEntity.ok(Map.of(
                    "comments", List.of(),
                    "totalCount", 0,
                    "totalPages", 0,
                    "currentPage", page,
                    "pageSize", pageSize,
                    "hasNext", false
                ));
            }
            
            // Get total count from first result
            int totalCount = ((Number) results.get(0).get("total_count")).intValue();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            boolean hasNext = (page + 1) < totalPages;
            
            // Transform results
            List<Map<String, Object>> comments = results.stream().map(row -> {
                Map<String, Object> comment = new HashMap<>();
                comment.put("id", row.get("id"));
                comment.put("messageId", row.get("message_id"));
                comment.put("content", row.get("content"));
                comment.put("createdAt", row.get("created_at"));
                comment.put("updatedAt", row.get("updated_at"));
                comment.put("parentCommentId", row.get("parent_comment_id"));
                comment.put("mediaUrl", row.get("media_url"));
                comment.put("mediaType", row.get("media_type"));
                
                // Add user data
                Map<String, Object> user = new HashMap<>();
                user.put("id", row.get("user_id"));
                user.put("username", row.get("username"));
                user.put("firstName", row.get("first_name"));
                user.put("lastName", row.get("last_name"));
                user.put("photo", row.get("photo"));
                comment.put("user", user);
                
                return comment;
            }).collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("comments", comments);
            response.put("totalCount", totalCount);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            response.put("pageSize", pageSize);
            response.put("hasNext", hasNext);
            
            logger.debug("Returning {} comments for message {} (total {})", comments.size(), messageId, totalCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting comments for message {}: {}", messageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to get comments: " + e.getMessage()));
        }
    }
    
    /**
     * Get replies for a comment
     */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<Map<String, Object>> getCommentReplies(@PathVariable Long commentId) {
        logger.debug("Getting replies for comment ID: {}", commentId);
        
        try {
            List<MessageComment> replies = engagementService.getCommentReplies(commentId);
            
            // Get user details for each reply
            List<Map<String, Object>> replyDetails = new ArrayList<>();
            for (MessageComment reply : replies) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", reply.getId());
                detail.put("messageId", reply.getMessageId());
                detail.put("userId", reply.getUserId());
                detail.put("content", reply.getContent());
                detail.put("mediaUrl", reply.getMediaUrl());
                detail.put("mediaType", reply.getMediaType());
                detail.put("createdAt", reply.getCreatedAt());
                detail.put("updatedAt", reply.getUpdatedAt());
                detail.put("parentCommentId", reply.getParentCommentId());
                
                // Add basic user info
                User user = userRepository.findById(reply.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                replyDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("replies", replyDetails);
            response.put("count", replies.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting comment replies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get comment replies: " + e.getMessage()));
        }
    }
    
    /**
     * Add a new comment to a message
     */
    @PostMapping("/{messageId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable Long messageId,
            @RequestBody Map<String, Object> commentData,
            HttpServletRequest request) {
        
        logger.debug("Adding comment to message: {}", messageId);
        
        try {
            // Extract user ID from request
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                       .body(Map.of("error", "User not authenticated"));
            }
            
            // Extract comment data
            String content = (String) commentData.get("content");
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                       .body(Map.of("error", "Comment content cannot be empty"));
            }
            
            // Get optional parent comment ID
            Long parentCommentId = null;
            if (commentData.containsKey("parentCommentId") && commentData.get("parentCommentId") != null) {
                parentCommentId = Long.valueOf(commentData.get("parentCommentId").toString());
            }
            
            // Create and save the comment
            MessageComment comment = new MessageComment();
            comment.setMessageId(messageId);
            comment.setUserId(userId);
            comment.setContent(content);
            comment.setCreatedAt(LocalDateTime.now());
            
            // Set optional fields
            if (commentData.containsKey("mediaUrl")) {
                comment.setMediaUrl((String) commentData.get("mediaUrl"));
            }
            
            if (commentData.containsKey("mediaType")) {
                comment.setMediaType((String) commentData.get("mediaType"));
            }
            
            if (parentCommentId != null) {
                comment.setParentCommentId(parentCommentId);
            }
            
            // Save comment
            MessageComment savedComment = engagementService.addComment(messageId, userId, content, parentCommentId);
            
            // Get user data for the response
            String sql = "SELECT username, first_name, last_name, photo FROM app_user WHERE id = ?";
            Map<String, Object> userData = jdbcTemplate.queryForMap(sql, userId);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedComment.getId());
            response.put("messageId", savedComment.getMessageId());
            response.put("content", savedComment.getContent());
            response.put("createdAt", savedComment.getCreatedAt());
            response.put("parentCommentId", savedComment.getParentCommentId());
            response.put("mediaUrl", savedComment.getMediaUrl());
            response.put("mediaType", savedComment.getMediaType());
            
            // Add user data
            Map<String, Object> user = new HashMap<>();
            user.put("id", userId);
            user.put("username", userData.get("username"));
            user.put("firstName", userData.get("first_name"));
            user.put("lastName", userData.get("last_name"));
            user.put("photo", userData.get("photo"));
            response.put("user", user);
            
            logger.debug("Comment added successfully to message {}: {}", messageId, savedComment.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error adding comment to message {}: {}", messageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to add comment: " + e.getMessage()));
        }
    }
    
    /**
     * Update an existing comment
     */
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> commentData,
            HttpServletRequest request) {
        logger.debug("Updating comment ID: {}", commentId);
        
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
            
            String content = commentData.get("content");
            if (content == null || content.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comment content is required"));
            }
            
            MessageComment updatedComment = engagementService.updateComment(commentId, userId, content);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComment.getId());
            response.put("messageId", updatedComment.getMessageId());
            response.put("userId", updatedComment.getUserId());
            response.put("content", updatedComment.getContent());
            response.put("createdAt", updatedComment.getCreatedAt());
            response.put("updatedAt", updatedComment.getUpdatedAt());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error updating comment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update comment: " + e.getMessage()));
        }
    }
    
    /**
     * Delete a comment
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Deleting comment ID: {}", commentId);
        
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
            
            engagementService.deleteComment(commentId, userId);
            
            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.error("Error deleting comment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete comment: " + e.getMessage()));
        }
    }
} 