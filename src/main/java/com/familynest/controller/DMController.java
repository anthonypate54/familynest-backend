package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            String userSql = "SELECT id, username, first_name, last_name FROM app_user WHERE id = ?";
            Map<String, Object> otherUser = jdbcTemplate.queryForMap(userSql, otherUserId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("conversationId", conversation.get("id"));
            response.put("createdAt", conversation.get("created_at"));
            response.put("otherUser", otherUser);

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

            // Get all conversations with last message info (simplified approach)
            String sql = """
                SELECT 
                    c.id as conversation_id,
                    c.created_at as conversation_created_at,
                    CASE 
                        WHEN c.user1_id = ? THEN c.user2_id 
                        ELSE c.user1_id 
                    END as other_user_id,
                    u.username as other_username,
                    u.first_name as other_first_name,
                    u.last_name as other_last_name
                FROM dm_conversation c
                JOIN app_user u ON (
                    (c.user1_id = ? AND u.id = c.user2_id) OR 
                    (c.user2_id = ? AND u.id = c.user1_id)
                )
                WHERE c.user1_id = ? OR c.user2_id = ?
                ORDER BY c.created_at DESC
                """;

            List<Map<String, Object>> conversations = jdbcTemplate.queryForList(sql, 
                currentUserId, currentUserId, currentUserId, currentUserId, currentUserId);

            return ResponseEntity.ok(Map.of("conversations", conversations));

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
     * Send a DM message
     * POST /api/dm/messages
     */
    @PostMapping("/messages")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> messageData) {
        logger.debug("Sending DM message");
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long senderId = authUtil.extractUserId(token);
            if (senderId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Extract message data
            Long conversationId = Long.parseLong(messageData.get("conversationId").toString());
            String content = (String) messageData.get("content");
            String mediaUrl = (String) messageData.get("mediaUrl");
            String mediaType = (String) messageData.get("mediaType");
            String mediaThumbnail = (String) messageData.get("mediaThumbnail");
            String mediaFilename = (String) messageData.get("mediaFilename");
            Long mediaSize = messageData.get("mediaSize") != null ? Long.parseLong(messageData.get("mediaSize").toString()) : null;
            Integer mediaDuration = messageData.get("mediaDuration") != null ? Integer.parseInt(messageData.get("mediaDuration").toString()) : null;

            // Validate conversation exists and user is part of it
            String validateSql = "SELECT user1_id, user2_id FROM dm_conversation WHERE id = ?";
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Long user1Id = (Long) conv.get("user1_id");
            Long user2Id = (Long) conv.get("user2_id");
            
            if (!senderId.equals(user1Id) && !senderId.equals(user2Id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Insert message
            String insertSql = """
                INSERT INTO dm_message 
                (conversation_id, sender_id, content, media_url, media_type, media_thumbnail, 
                 media_filename, media_size, media_duration, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) 
                RETURNING id, created_at
                """;
            
            Map<String, Object> newMessage = jdbcTemplate.queryForMap(insertSql,
                conversationId, senderId, content, mediaUrl, mediaType, mediaThumbnail,
                mediaFilename, mediaSize, mediaDuration, Timestamp.valueOf(LocalDateTime.now()));

            // Update conversation timestamp
            String updateConvSql = "UPDATE dm_conversation SET updated_at = ? WHERE id = ?";
            jdbcTemplate.update(updateConvSql, Timestamp.valueOf(LocalDateTime.now()), conversationId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("messageId", newMessage.get("id"));
            response.put("conversationId", conversationId);
            response.put("senderId", senderId);
            response.put("content", content);
            response.put("mediaUrl", mediaUrl);
            response.put("mediaType", mediaType);
            response.put("createdAt", newMessage.get("created_at"));

            logger.debug("Message sent successfully: {}", newMessage.get("id"));
            return ResponseEntity.ok(response);

        } catch (DataAccessException e) {
            logger.error("Database error sending message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
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
            Long user1Id = (Long) conv.get("user1_id");
            Long user2Id = (Long) conv.get("user2_id");
            
            if (!currentUserId.equals(user1Id) && !currentUserId.equals(user2Id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Get messages with pagination
            int offset = page * size;
            String messagesSql = """
                SELECT 
                    m.id, m.sender_id, m.content, m.media_url, m.media_type, 
                    m.media_thumbnail, m.media_filename, m.media_size, m.media_duration,
                    m.is_read, m.created_at,
                    u.username as sender_username, u.first_name as sender_first_name, u.last_name as sender_last_name
                FROM dm_message m
                JOIN app_user u ON m.sender_id = u.id
                WHERE m.conversation_id = ?
                ORDER BY m.created_at DESC
                LIMIT ? OFFSET ?
                """;

            List<Map<String, Object>> messages = jdbcTemplate.queryForList(messagesSql, conversationId, size, offset);

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

            return ResponseEntity.ok(response);

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
            Long user1Id = (Long) conv.get("user1_id");
            Long user2Id = (Long) conv.get("user2_id");
            
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