package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import com.familynest.dto.DMMessagePayload;
import com.familynest.service.MediaService;
import com.familynest.service.WebSocketBroadcastService;
import com.familynest.service.PushNotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.Math;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/dm")
public class DMController {

    private static final Logger logger = LoggerFactory.getLogger(DMController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private WebSocketBroadcastService webSocketBroadcastService;

    @Autowired
    private PushNotificationService pushNotificationService;

    /**
     * Get or create a conversation with another user
     * POST /api/dm/conversations/{otherUserId}
     */
    @PostMapping("/conversations/{otherUserId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> getOrCreateConversation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long otherUserId) {
        logger.debug("Getting/creating conversation with user: {}", otherUserId);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Can't DM yourself
            if (currentUserId.equals(otherUserId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot create conversation with yourself"));
            }

            // Ensure consistent ordering for conversation lookup
            Long user1Id = Math.min(currentUserId, otherUserId);
            Long user2Id = Math.max(currentUserId, otherUserId);

            // Check if conversation already exists
            String checkSql = "SELECT id, created_at FROM dm_conversation WHERE user1_id = ? AND user2_id = ?";
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, user1Id, user2Id);

            Map<String, Object> conversation;
            if (!existing.isEmpty()) {
                // Return existing conversation
                conversation = existing.get(0);
                logger.debug("Found existing conversation: {}", conversation.get("id"));
            } else {
                // Create new conversation
                String insertSql = "INSERT INTO dm_conversation (user1_id, user2_id, created_at) VALUES (?, ?, ?) RETURNING id, created_at";
                conversation = jdbcTemplate.queryForMap(insertSql, user1Id, user2Id, Timestamp.valueOf(LocalDateTime.now()));
                logger.debug("Created new conversation: {}", conversation.get("id"));
            }

            // Get other user info
            String userSql = "SELECT id, username, first_name, last_name, photo FROM app_user WHERE id = ?";
            Map<String, Object> otherUser = jdbcTemplate.queryForMap(userSql, otherUserId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("conversation_id", conversation.get("id"));
            response.put("created_at", conversation.get("created_at"));
            response.put("other_user", otherUser);

            return ResponseEntity.ok(response);

        } catch (DataAccessException e) {
            logger.error("Database error getting/creating conversation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error getting/creating conversation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /**
     * Get all conversations for the current user
     * GET /api/dm/conversations
     */
    @GetMapping("/conversations")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getUserConversations(
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting conversations for user");
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Get all conversations with last message info (fixed SQL query)
            String sql = """
                SELECT 
                    c.id as conversation_id,
                    c.created_at as created_at,
                    u.id as other_user_id,
                    u.username as other_username,
                    u.first_name as other_first_name,
                    u.last_name as other_last_name,
                    u.photo as other_user_photo,
                    m.content as last_message_content,
                    m.created_at as last_message_created_at
                FROM dm_conversation c
                JOIN app_user u ON (
                    (c.user1_id = ? AND u.id = c.user2_id) OR 
                    (c.user2_id = ? AND u.id = c.user1_id)
                )
                LEFT JOIN (
                    SELECT DISTINCT ON (conversation_id) 
                        conversation_id, content, created_at
                    FROM dm_message
                    ORDER BY conversation_id, created_at DESC
                ) m ON m.conversation_id = c.id
                WHERE c.user1_id = ? OR c.user2_id = ?
                ORDER BY COALESCE(m.created_at, c.created_at) DESC                
            """;

            List<Map<String, Object>> conversations = jdbcTemplate.queryForList(sql, 
                currentUserId, currentUserId, currentUserId, currentUserId);

            // Transform the data to match getOrCreateConversation format
            List<Map<String, Object>> formattedConversations = conversations.stream()
                .map(conv -> {
                    Map<String, Object> otherUser = new HashMap<>();
                    otherUser.put("id", conv.get("other_user_id"));
                    otherUser.put("username", conv.get("other_username"));
                    otherUser.put("first_name", conv.get("other_first_name"));
                    otherUser.put("last_name", conv.get("other_last_name"));
                    otherUser.put("photo", conv.get("other_user_photo"));
                    otherUser.put("last_message_content", conv.get("last_message_content"));
                    otherUser.put("last_message_created_at", conv.get("last_message_created_at"));

                    Map<String, Object> formattedConv = new HashMap<>();
                    formattedConv.put("conversation_id", conv.get("conversation_id"));
                    formattedConv.put("created_at", conv.get("created_at"));
                    formattedConv.put("other_user", otherUser);
                    return formattedConv;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(Map.of("conversations", formattedConversations));

        } catch (DataAccessException e) {
            logger.error("Database error getting conversations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error getting conversations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /**
     * Send a DM message with media (adapted from UserController.postMessage)
     * POST /api/dm/{userId}/message
     */
    @Transactional
    @PostMapping(value = "/{userId}/message", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> postMessage(
            @PathVariable Long userId,
            @RequestParam("content") String content,
            @RequestParam(value = "media", required = false) MultipartFile media,
            @RequestParam(value = "mediaType", required = false) String mediaType,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam("conversationId") Long conversationId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Posting DM message with media for user: {}", userId);
        
        try {
            // Validation
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                       .body(Map.of("error", "Message content cannot be empty"));
            }
    
            // Extract user ID from token for authorization
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long tokenUserId = authUtil.extractUserId(token);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }
            
            // Ensure the token user matches the path userId
            if (!tokenUserId.equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this user"));
            }
            
            Long senderId = userId;

            // Validate conversation exists and user is part of it
            String validateSql = "SELECT user1_id, user2_id FROM dm_conversation WHERE id = ?";
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Long user1Id = ((Number) conv.get("user1_id")).longValue();
            Long user2Id = ((Number) conv.get("user2_id")).longValue();

            if (!senderId.equals(user1Id) && !senderId.equals(user2Id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }
    
            // Get user data for sender_username, etc.
            String userSql = "SELECT username, first_name, last_name, photo FROM app_user WHERE id = ?";
            Map<String, Object> userData = jdbcTemplate.queryForMap(userSql, senderId);
    
            // Handle media upload if present
            String mediaUrl = null;
            String thumbnailUrl = null;
            
            // Handle regular media upload first (but not if we have external video URL)
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
    
            // Insert the DM message and get the new ID (fixed parameter count)
            String insertSql = "INSERT INTO dm_message (conversation_id, sender_id, content, " +
                "media_url, media_type, media_thumbnail, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
    
            Long newMessageId = jdbcTemplate.queryForObject(insertSql, Long.class,
                conversationId,
                senderId,
                content, 
                mediaUrl,
                mediaType,
                thumbnailUrl,
                Timestamp.valueOf(LocalDateTime.now())
            );
    
            // Fetch the full DM message with sender info
            String fetchSql = """
                SELECT 
                    dm.id, dm.conversation_id, dm.sender_id, dm.content, 
                    dm.media_url, dm.media_type, dm.media_thumbnail, 
                    dm.media_filename, dm.media_size, dm.media_duration, dm.created_at,
                    u.username as sender_username, u.first_name as sender_first_name, 
                    u.last_name as sender_last_name, u.photo as sender_photo
                FROM dm_message dm
                JOIN app_user u ON dm.sender_id = u.id
                WHERE dm.id = ?
                """;
            
            Map<String, Object> messageData = jdbcTemplate.queryForMap(fetchSql, newMessageId);
             // Transform to response format
            Map<String, Object> response = new HashMap<>();
            response.put("id", messageData.get("id"));
            response.put("conversation_id", messageData.get("conversation_id"));
            response.put("sender_id", messageData.get("sender_id"));
            response.put("content", messageData.get("content"));
            response.put("media_url", messageData.get("media_url"));
            response.put("media_type", messageData.get("media_type"));
            response.put("media_thumbnail", messageData.get("media_thumbnail"));
            response.put("media_filename", messageData.get("media_filename"));
            response.put("media_size", messageData.get("media_size"));
            response.put("media_duration", messageData.get("media_duration"));
            response.put("created_at", messageData.get("created_at"));
            response.put("sender_username", messageData.get("sender_username"));
            response.put("sender_first_name", messageData.get("sender_first_name"));
            response.put("sender_last_name", messageData.get("sender_last_name"));
            response.put("sender_photo", messageData.get("sender_photo"));
    
            // Determine recipient ID (the other user in the conversation)
            Long recipientId = senderId.equals(user1Id) ? user2Id : user1Id;

            // Broadcast the raw database result directly
            logger.debug("Broadcasting DM message: {}", messageData);
            webSocketBroadcastService.broadcastDMMessage(messageData, recipientId);
            
            // Send push notification to recipient (background notification)
            try {
                String senderName = (String) userData.get("username");
                pushNotificationService.sendDMNotification(newMessageId, recipientId, senderName, content);
            } catch (Exception pushError) {
                logger.error("Error sending DM push notification for message {}: {}", newMessageId, pushError.getMessage());
                // Don't let push notification errors break the DM posting flow
            }
            
      // Return the fully-formed message as the response
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(response);
    
        } catch (Exception e) {
            logger.error("Error posting DM message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to post DM message: " + e.getMessage()));
        }
    }

    /**
     * Get messages for a conversation
     * GET /api/dm/conversations/{conversationId}/messages
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getConversationMessages(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        logger.debug("Getting messages for conversation: {}", conversationId);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Validate user is part of this conversation
            String validateSql = "SELECT user1_id, user2_id FROM dm_conversation WHERE id = ?";
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Long user1Id = ((Number) conv.get("user1_id")).longValue();
            Long user2Id = ((Number) conv.get("user2_id")).longValue();
            
            if (!currentUserId.equals(user1Id) && !currentUserId.equals(user2Id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Get messages with pagination
            int offset = page * size;
            String messagesSql = """
                SELECT 
                    m.id, m.conversation_id, m.sender_id, m.content, m.media_url, m.media_type, 
                    m.media_thumbnail, m.media_filename, m.media_size, m.media_duration,
                    m.is_read, m.created_at,
                    u.username as sender_username, u.first_name as sender_first_name, u.last_name as sender_last_name,
                    u.photo as sender_photo
                FROM dm_message m
                JOIN app_user u ON m.sender_id = u.id
                WHERE m.conversation_id = ?
                ORDER BY m.created_at DESC
                LIMIT ? OFFSET ?
                """;

            List<Map<String, Object>> messages = jdbcTemplate.queryForList(messagesSql, conversationId, size, offset);
            
            // Debug log each message
            for (Map<String, Object> message : messages) {
                logger.debug("Message data: {}", message);
            }

            // Get total count
            String countSql = "SELECT COUNT(*) FROM dm_message WHERE conversation_id = ?";
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, conversationId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("messages", messages);
            response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "totalCount", totalCount,
                "totalPages", (int) Math.ceil((double) totalCount / size)
            ));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(response);

        } catch (DataAccessException e) {
            logger.error("Database error getting messages: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error getting messages: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /**
     * Mark messages as read
     * PUT /api/dm/conversations/{conversationId}/read
     */
    @PutMapping("/conversations/{conversationId}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markMessagesAsRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId) {
        logger.debug("Marking messages as read for conversation: {}", conversationId);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Validate user is part of this conversation
            String validateSql = "SELECT user1_id, user2_id FROM dm_conversation WHERE id = ?";
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Long user1Id = ((Number) conv.get("user1_id")).longValue();
            Long user2Id = ((Number) conv.get("user2_id")).longValue();
            
            if (!currentUserId.equals(user1Id) && !currentUserId.equals(user2Id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Mark all unread messages from the other user as read
            String updateSql = """
                UPDATE dm_message 
                SET is_read = true 
                WHERE conversation_id = ? 
                AND sender_id != ? 
                AND is_read = false
                """;
            
            int updatedCount = jdbcTemplate.update(updateSql, conversationId, currentUserId);

            logger.debug("Marked {} messages as read", updatedCount);
            return ResponseEntity.ok(Map.of("markedAsRead", updatedCount));

        } catch (DataAccessException e) {
            logger.error("Database error marking messages as read: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error marking messages as read: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }
} 