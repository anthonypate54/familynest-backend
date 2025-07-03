package com.familynest.controller;

import com.familynest.model.MessageComment;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import com.familynest.service.ThumbnailService;
import com.familynest.service.MediaService;
import com.familynest.service.MessageService;
import com.familynest.service.WebSocketBroadcastService;
import com.familynest.model.Message;
import java.sql.Timestamp;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.familynest.auth.AuthUtil; // Add this import


import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/messages")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private VideoController videoController;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private AuthUtil authUtil; // Add this dependency

    @Autowired
    private WebSocketBroadcastService webSocketBroadcastService;

    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;

    @Value("${app.videos.dir:${file.upload-dir}/videos}")
    private String videosDir;
    
    @Value("${app.thumbnail.dir:${file.upload-dir}/thumbnails}")
    private String thumbnailDir;
    
    @Value("${app.url.videos:/uploads/videos}")
    private String videosUrlPath;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;
    
    @Value("${app.url.images:/uploads/images}")
    private String imagesUrlPath;

    /**
     * Get comments for a message with efficient pagination
     * Uses a single optimized SQL query instead of multiple Hibernate queries
     */
    @GetMapping("/{messageId}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getComments(
            @PathVariable Long messageId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
            
        logger.debug("Getting comments for message: {}", messageId);
        
        try {
            // Limit page size to prevent abuse
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                logger.debug("No userId found in request");
                throw new RuntimeException("Unauthorized");
            }
                
            // Use a single optimized query to get comments with user data and metrics
            String sql = "WITH user_check AS (" +
            "  SELECT id FROM app_user WHERE id = ?" +
            "), " +
            "user_families AS (" +
            "  SELECT ufm.family_id " +
            "  FROM user_family_membership ufm " +
            "  WHERE ufm.user_id = ? " +
            "), " +
            "muted_families AS (" +
            "  SELECT ufms.family_id " +
            "  FROM user_family_message_settings ufms " +
            "  WHERE ufms.user_id = ? AND ufms.receive_messages = false" +
            "), " +
            "active_families AS (" +
            "  SELECT uf.family_id " +
            "  FROM user_families uf " +
            "  LEFT JOIN muted_families mf ON uf.family_id = mf.family_id " +
            "  WHERE mf.family_id IS NULL" +
            "), " +
            "message_subset AS (" +
            "  SELECT m.id " +
            "  FROM message_comment m " +
            "  JOIN message_comment_family_link mcfl2 ON m.id = mcfl2.message_comment_id " +
            "  JOIN active_families af ON mcfl2.family_id = af.family_id " +
            "  WHERE m.parent_message_id = ? " +
            "  ORDER BY m.id ASC " + 
            "  LIMIT 100" +
            ") " +
            "SELECT DISTINCT " +
            "  m.id, m.content, m.sender_username, m.sender_id, " +
            "  m.timestamp, m.media_type, m.media_url, m.thumbnail_url, " +
            "  s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
            "  m.parent_message_id as parent_message_id, " +
            "  COALESCE(vc.count, 0) as view_count, " +
            "  m.like_count, m.love_count, " +
            "  COALESCE(cc.count, 0) as comment_count, " +
            "  CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked, " +
            "  CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved " +
            "FROM message_comment m " +
            "JOIN message_subset ms ON m.id = ms.id " +
            "LEFT JOIN message_comment_family_link mcfl ON m.id = mcfl.message_comment_id " +
            "LEFT JOIN family f ON mcfl.family_id = f.id " +
            "LEFT JOIN app_user s ON m.sender_id = s.id " +
            "LEFT JOIN (SELECT message_id, COUNT(*) as count FROM message_view GROUP BY message_id) vc " +
            "  ON m.id = vc.message_id " +
            "LEFT JOIN (SELECT parent_message_id, COUNT(*) as count FROM message_comment GROUP BY parent_message_id) cc " +
            "  ON m.id = cc.parent_message_id " +
            "LEFT JOIN message_reaction mr ON m.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'COMMENT' " +
            "LEFT JOIN message_reaction mr2 ON m.id = mr2.message_id AND mr2.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'COMMENT' " +
            "ORDER BY m.id ASC";
            // Execute query
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, userId, userId, messageId, userId, userId);
            // Debug output only for development - just log count, not each individual message
            if (logger.isDebugEnabled() && !results.isEmpty()) {
                logger.debug("Number of messages retrieved: {}", results.size());
                
                // Only log details of first message as sample
                if (results.size() > 0) {
                    Map<String, Object> firstResult = results.get(0);
                    logger.debug("Sample message data - ID: {}, timestamp: {}, has thumbnail: {}", 
                        firstResult.get("id"), 
                        firstResult.get("timestamp"),
                        firstResult.get("thumbnail_url") != null);
                }
            }
                         
            // Transform results to the response format
            List<Map<String, Object>> response = results.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", message.get("id"));
                messageMap.put("content", message.get("content"));
                messageMap.put("senderUsername", message.get("sender_username"));
                messageMap.put("senderId", message.get("sender_id"));
                messageMap.put("senderPhoto", message.get("sender_photo"));
                messageMap.put("senderFirstName", message.get("sender_first_name"));
                messageMap.put("senderLastName", message.get("sender_last_name"));
                messageMap.put("timestamp", message.get("timestamp").toString());
                messageMap.put("mediaType", message.get("media_type"));
                messageMap.put("mediaUrl", message.get("media_url"));
                messageMap.put("thumbnailUrl", message.get("thumbnail_url"));
                messageMap.put("viewCount", message.get("view_count"));
                messageMap.put("likeCount", message.get("like_count"));
                messageMap.put("loveCount", message.get("love_count"));
                messageMap.put("commentCount", message.get("comment_count"));
                messageMap.put("parentMessageId", message.get("parent_message_id"));
                messageMap.put("isLiked", message.get("is_liked"));
                messageMap.put("isLoved", message.get("is_loved"));
                
                // Add video message thumbnail URL warning only once
                if ("video".equals(message.get("media_type")) && message.get("thumbnail_url") == null) {
                    logger.debug("Video message ID {} has no thumbnail_url", message.get("id"));
                }
                
                return messageMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} messages for user {} using a single optimized query", response.size(), userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving messages: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get replies for a comment
     */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<Map<String, Object>> getCommentReplies(@PathVariable Long commentId) {
        logger.debug("Getting replies for comment ID: {}", commentId);
        
        try {
            // Get replies with a single optimized query
            String sql = "SELECT mc.*, u.username, u.first_name, u.last_name, u.photo, " +
                        "mc.like_count, mc.love_count, " +
                        "COALESCE(cc.count, 0) as reply_count " +
                        "FROM message_comment mc " +
                        "JOIN app_user u ON mc.sender_id = u.id " +
                        "LEFT JOIN (SELECT parent_comment_id, COUNT(*) as count FROM message_comment GROUP BY parent_comment_id) cc " +
                        "  ON mc.id = cc.parent_comment_id " +
                        "WHERE mc.parent_message_id = ? " +
                        "ORDER BY mc.created_at ASC";
            
            List<Map<String, Object>> replies = jdbcTemplate.queryForList(sql, commentId);
            
            // Transform results
            List<Map<String, Object>> replyDetails = replies.stream().map(row -> {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", row.get("id"));
                detail.put("parentMessageId", row.get("parent_message_id"));
                detail.put("senderId", row.get("sender_id"));
                detail.put("content", row.get("content"));
                detail.put("mediaUrl", row.get("media_url"));
                detail.put("mediaType", row.get("media_type"));
                detail.put("thumbnailUrl", row.get("thumbnail_url"));
                detail.put("videoUrl", row.get("video_url"));
                detail.put("createdAt", row.get("created_at"));
                detail.put("updatedAt", row.get("updated_at"));
                detail.put("parentCommentId", row.get("parent_comment_id"));
                detail.put("metrics", row.get("metrics"));
                
                // Add user data
                Map<String, Object> user = new HashMap<>();
                user.put("id", row.get("sender_id"));
                user.put("username", row.get("username"));
                user.put("firstName", row.get("first_name"));
                user.put("lastName", row.get("last_name"));
                user.put("photo", row.get("photo"));
                detail.put("user", user);
                
                return detail;
            }).collect(Collectors.toList());
            
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
    
    @PostMapping(value = "/comments/{commentId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCommentMedia(
        @PathVariable Long commentId,
        @RequestPart("media") MultipartFile media,
        @RequestParam("mediaType") String mediaType,
        HttpServletRequest request
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            logger.debug("Processing media of type: {} for comment ID: {}", mediaType, commentId);
            
            // Create directory structure if it doesn't exist
            String subdir = "video".equals(mediaType) ? "videos" : "images";
            Path uploadPath = Paths.get(uploadDir, subdir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Create filename with timestamp - using simple naming pattern
            String mediaFileName = System.currentTimeMillis() + "_" + media.getOriginalFilename();
            Path mediaPath = uploadPath.resolve(mediaFileName);
            
            // Write the file
            Files.write(mediaPath, media.getBytes());
            logger.debug("Media file saved at: {}", mediaPath);
            
            // Set media URL (relative path)
            // Use URLs matching the static-path-pattern configuration
            String mediaUrl = "video".equals(mediaType) ? 
                videosUrlPath + "/" + mediaFileName : 
                imagesUrlPath + "/" + mediaFileName;
            
            response.put("mediaType", mediaType);
            response.put("mediaUrl", mediaUrl);
            
            String thumbnailUrl = null;
            
            // For videos, use VideoController to get thumbnail
            if ("video".equals(mediaType)) {
                thumbnailUrl = videoController.getThumbnailForVideo(mediaUrl);
                if (thumbnailUrl != null) {
                    response.put("thumbnailUrl", thumbnailUrl);
                    logger.debug("Got thumbnail URL from VideoController: {}", thumbnailUrl);
                } else {
                    logger.warn("Failed to get thumbnail URL from VideoController");
                }
            }

            // Update the comment in the database with the media URLs
            String updateSql = "UPDATE message_comment SET media_url = ?, media_type = ?, thumbnail_url = ? WHERE id = ?";
            jdbcTemplate.update(updateSql, mediaUrl, mediaType, thumbnailUrl, commentId);
            logger.debug("Updated comment {} with media URLs", commentId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading media: {}", e.getMessage(), e);
            response.put("error", "Failed to upload media: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    /* 
 
    /**
     * 
     * Add a new comment to a message
     */
    @Transactional
    @PostMapping(value = "/{parentMessageId}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> postComment(
        @PathVariable Long parentMessageId, 
        @RequestParam("content") String content,
        @RequestParam(value = "media", required = false) MultipartFile media,
        @RequestParam(value = "mediaType", required = false) String mediaType,
        @RequestParam(value = "videoUrl", required = false) String videoUrl,  // ADD THIS
        @RequestParam(value = "familyId", required = false) Long familyId,
        @RequestHeader("Authorization") String authHeader,
        HttpServletRequest request) {
        try {
            // Validation
            if (parentMessageId <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                       .body(Map.of("error", "Message ID is required"));
            }
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                       .body(Map.of("error", "Comment content cannot be empty"));
            }
    
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
    
            // Get user data for sender_username, etc.
            String sql = "SELECT username, first_name, last_name, photo FROM app_user WHERE id = ?";
            Map<String, Object> userData = jdbcTemplate.queryForMap(sql, userId);
    
            // Handle media upload if present
            String mediaUrl = null;
            String thumbnailUrl = null;
            if (media != null && !media.isEmpty() && (videoUrl == null || !videoUrl.startsWith("http"))) {
                Map<String, String> mediaResult = mediaService.uploadMedia(media, mediaType);
                mediaUrl = mediaResult.get("mediaUrl");
                if ("video".equals(mediaType)) {
                    thumbnailUrl = mediaResult.get("thumbnailUrl");
                }
            } // Handle external video URL (takes priority and may override above)
            else if (videoUrl != null && videoUrl.startsWith("http")) {
                logger.debug("Processing external video URL: {}", videoUrl);
                
                // If we uploaded media, it's actually a thumbnail for the external video
                if (media != null && !media.isEmpty() && "image".equals(mediaType)) {
                    // Use our new clean method instead of reassignment
                    Map<String, String> externalVideoResult = mediaService.processExternalVideoWithThumbnail(media, videoUrl);
                    mediaUrl = externalVideoResult.get("mediaUrl");
                    thumbnailUrl = externalVideoResult.get("thumbnailUrl");
                    mediaType = externalVideoResult.get("mediaType");
                    logger.debug("Used new method - mediaUrl: {}, thumbnailUrl: {}", mediaUrl, thumbnailUrl);
                } else {
                    // No thumbnail uploaded
                    mediaUrl = videoUrl;
                    mediaType = "cloud_video";
                    thumbnailUrl = null;
                    logger.debug("External video without thumbnail - mediaUrl: {}", mediaUrl);
                }
            }
        
            // Insert the comment with family_id = NULL (using new schema)
            String insertSql = "INSERT INTO message_comment (content, user_id, sender_id, sender_username, " +
                "media_type, media_url, thumbnail_url, family_id, parent_message_id, like_count, love_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 0, 0) RETURNING id";
    
            Long newCommentId = jdbcTemplate.queryForObject(insertSql, Long.class,
                content, 
                userId, 
                userId, 
                userData.get("username"),
                mediaType,
                mediaUrl,
                thumbnailUrl,
                parentMessageId
            );
            
            // Find all families where the parent message is visible
            String findFamiliesSql = "SELECT family_id FROM message_family_link WHERE message_id = ?";
            List<Map<String, Object>> parentMessageFamilies = jdbcTemplate.queryForList(findFamiliesSql, parentMessageId);
            
            // Insert comment into message_comment_family_link table for each family
            String linkSql = "INSERT INTO message_comment_family_link (message_comment_id, family_id) VALUES (?, ?)";
            for (Map<String, Object> family : parentMessageFamilies) {
                Long targetFamilyId = ((Number) family.get("family_id")).longValue();
                jdbcTemplate.update(linkSql, newCommentId, targetFamilyId);
                logger.debug("Linked comment {} to family {}", newCommentId, targetFamilyId);
            }
            
            logger.debug("Created comment {} visible in {} families", newCommentId, parentMessageFamilies.size());
    
            // Fetch the full comment with all joins using the service
            Map<String, Object> commentData = messageService.getCommentById(newCommentId);
    
            // Broadcast the comment via WebSocket using thread-specific approach
            // This separates concerns from main message broadcasts
            try {
                logger.debug("Broadcasting comment via thread-specific WebSocket: {}", commentData);
                // Broadcast to each family the parent message is visible in
                for (Map<String, Object> family : parentMessageFamilies) {
                    Long targetFamilyId = ((Number) family.get("family_id")).longValue();
                    webSocketBroadcastService.broadcastComment(commentData, parentMessageId, targetFamilyId);
                }
            } catch (Exception e) {
                logger.error("Error broadcasting WebSocket comment for commentId {}: {}", newCommentId, e.getMessage());
                // Continue processing - don't let WebSocket errors break comment posting
            }

            // Broadcast updated comment count for parent message (exclude comment poster)
            try {
                // Calculate current comment count dynamically from message_comment table
                String getCommentCountSql = "SELECT COUNT(*) FROM message_comment WHERE parent_message_id = ?";
                Integer commentCount = jdbcTemplate.queryForObject(getCommentCountSql, Integer.class, parentMessageId);
                
                // Broadcast comment count update to each family (excluding the comment poster)
                for (Map<String, Object> family : parentMessageFamilies) {
                    Long targetFamilyId = ((Number) family.get("family_id")).longValue();
                    webSocketBroadcastService.broadcastCommentCountExcludingUser(parentMessageId, 
                        commentCount != null ? commentCount : 0, targetFamilyId, userId);
                }
                logger.debug("Broadcasted comment count update for message {} (count: {}) excluding poster {}", 
                    parentMessageId, commentCount, userId);
            } catch (Exception e) {
                logger.error("Error broadcasting comment count update for messageId {}: {}", parentMessageId, e.getMessage());
                // Continue processing - don't let WebSocket errors break comment posting
            }
    
            // Return the fully-formed comment as the response
            return ResponseEntity.status(HttpStatus.CREATED).body(commentData);
    
        } catch (Exception e) {
            logger.error("Error posting comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to post message: " + e.getMessage()));
        }
    }

/**
 * Update an existing comment
 */
@Transactional
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

            // Verify comment exists and belongs to user
            String checkSql = "SELECT id FROM message_comment WHERE id = ? AND sender_id = ?";
            try {
                jdbcTemplate.queryForObject(checkSql, Long.class, commentId, userId);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Comment not found or you don't have permission to update it"));
            }
            
            // Update the comment
            String updateSql = "UPDATE message_comment SET content = ?, updated_at = CURRENT_TIMESTAMP " +
                             "WHERE id = ? AND sender_id = ? " +
                             "RETURNING id, parent_message_id, sender_id, content, timestamp, updated_at";
            
            Map<String, Object> updatedComment = jdbcTemplate.queryForMap(updateSql, content, commentId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComment.get("id"));
            response.put("parentMessageId", updatedComment.get("parent_message_id"));
            response.put("senderId", updatedComment.get("sender_id"));
            response.put("content", updatedComment.get("content"));
            response.put("createdAt", updatedComment.get("created_at"));
            response.put("updatedAt", updatedComment.get("updated_at"));
            
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
    @Transactional
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

            // Verify comment exists and belongs to user
            String checkSql = "SELECT id FROM message_comment WHERE id = ? AND sender_id = ?";
            try {
                jdbcTemplate.queryForObject(checkSql, Long.class, commentId, userId);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Comment not found or you don't have permission to delete it"));
            }
            
            // Delete the comment
            String deleteSql = "DELETE FROM message_comment WHERE id = ? AND sender_id = ?";
            int rowsAffected = jdbcTemplate.update(deleteSql, commentId, userId);
            
            if (rowsAffected == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Comment not found"));
            }
            
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

    private Message mapToMessage(Map<String, Object> row) {
        Message message = new Message();
        message.setId(((Number) row.get("id")).longValue());
        message.setContent((String) row.get("content"));
        message.setSenderUsername((String) row.get("sender_username"));
        message.setSenderId(((Number) row.get("sender_id")).longValue());
        message.setFamilyId(((Number) row.get("family_id")).longValue());
        message.setTimestamp(((Timestamp) row.get("timestamp")).toLocalDateTime());
        message.setMediaType((String) row.get("media_type"));
        message.setMediaUrl((String) row.get("media_url"));
        message.setThumbnailUrl((String) row.get("thumbnail_url"));
        message.setSenderPhoto((String) row.get("sender_photo"));
        message.setSenderFirstName((String) row.get("sender_first_name"));
        message.setSenderLastName((String) row.get("sender_last_name"));
        message.setFamilyName((String) row.get("family_name"));
        message.setParentMessageId(((Number) row.get("parent_message_id")).longValue());
        message.setViewCount(((Number) row.get("view_count")).intValue());
        message.setLikeCount(((Number) row.get("like_count")).intValue());
        message.setLoveCount(((Number) row.get("love_count")).intValue());
        message.setCommentCount(((Number) row.get("comment_count")).intValue());
        message.setIsLiked(((Boolean) row.get("is_liked")));
        message.setIsLoved(((Boolean) row.get("is_loved")));
        return message;
    }
} 