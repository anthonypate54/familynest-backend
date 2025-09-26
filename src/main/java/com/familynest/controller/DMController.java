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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.Math;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/dm")
public class DMController {

    private static final Logger logger = LoggerFactory.getLogger(DMController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Check if a recipient has muted the sender
     */
    private boolean isRecipientMutedBySender(Long recipientId, Long senderId) {
        try {
            String muteSql = """
                SELECT COUNT(*) > 0 
                FROM user_member_message_settings umms 
                WHERE umms.user_id = ? 
                AND umms.member_user_id = ? 
                AND umms.receive_messages = false
                """;
            Boolean isMuted = jdbcTemplate.queryForObject(muteSql, Boolean.class, recipientId, senderId);
            return isMuted != null && isMuted;
        } catch (Exception e) {
            logger.error("Error checking mute status for recipient {} and sender {}: {}", recipientId, senderId, e.getMessage());
            return false; // Default to not muted on error
        }
    }

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

    // Group Chat Configuration
    @Value("${app.groupchat.max-participants:5}")
    private int maxGroupChatParticipants;

    /**
     * Get group chat configuration
     * GET /api/dm/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getGroupChatConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxParticipants", maxGroupChatParticipants);
        config.put("minParticipants", 1); // Minimum participants to create a group (excluding creator)
        
        return ResponseEntity.ok(config);
    }

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
                
                // Broadcast new conversation to both participants so their conversation lists refresh
                Long conversationId = ((Number) conversation.get("id")).longValue();
                
                // Schedule WebSocket broadcast to happen AFTER transaction commits
                final Long finalCurrentUserId = currentUserId;
                final Long finalOtherUserId = otherUserId;
                final Long finalConversationId = conversationId;
                
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        Map<String, Object> notificationData = new HashMap<>();
                        notificationData.put("type", "new_conversation");
                        notificationData.put("conversationId", finalConversationId);
                        notificationData.put("isGroup", false);
                        
                        // Notify both participants
                        webSocketBroadcastService.broadcastDMMessage(notificationData, finalCurrentUserId);
                        webSocketBroadcastService.broadcastDMMessage(notificationData, finalOtherUserId);
                        logger.debug("Broadcasted new 1:1 conversation notification to users: {} and {} (AFTER COMMIT)", finalCurrentUserId, finalOtherUserId);
                    }
                });
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

            // Get all conversations (1:1 and groups) with last message info
            String sql = """
                SELECT 
                    c.id as conversation_id,
                    c.name as group_name,
                    c.is_group,
                    c.created_by_user_id as created_by,
                    c.created_at as created_at,
                    u.id as other_user_id,
                    u.username as other_username,
                    u.first_name as other_first_name,
                    u.last_name as other_last_name,
                    u.photo as other_user_photo,
                    m.content as last_message_content,
                    m.created_at as last_message_created_at,
                    CASE 
                        WHEN c.is_group = TRUE THEN (
                            SELECT COUNT(*) 
                            FROM dm_conversation_participant dcp 
                            WHERE dcp.conversation_id = c.id
                        )
                        ELSE 2
                    END as participant_count
                FROM dm_conversation c
                -- Left join with participants to include conversations user belongs to (both group and 1:1)
                LEFT JOIN dm_conversation_participant my_participation ON c.id = my_participation.conversation_id AND my_participation.user_id = ?
                -- For 1:1 chats, get the other user info
                LEFT JOIN app_user u ON (
                    c.is_group = FALSE AND (
                        (c.user1_id = ? AND u.id = c.user2_id) OR 
                        (c.user2_id = ? AND u.id = c.user1_id)
                    )
                )
                LEFT JOIN (
                    SELECT DISTINCT ON (conversation_id) 
                        conversation_id, content, created_at
                    FROM dm_message
                    ORDER BY conversation_id, created_at DESC
                ) m ON m.conversation_id = c.id
                WHERE (
                    -- Include group conversations where user is a participant
                    (c.is_group = TRUE AND my_participation.user_id IS NOT NULL) OR
                    -- Include 1:1 conversations where user is user1 or user2
                    (c.is_group = FALSE AND (c.user1_id = ? OR c.user2_id = ?))
                )
                ORDER BY COALESCE(m.created_at, c.created_at) DESC                
            """;

            List<Map<String, Object>> rawConversations = jdbcTemplate.queryForList(sql, 
                currentUserId, currentUserId, currentUserId, currentUserId, currentUserId);

            // Process and format conversations
            List<Map<String, Object>> formattedConversations = new ArrayList<>();
            
            for (Map<String, Object> conv : rawConversations) {
                Map<String, Object> formatted = new HashMap<>();
                
                // Common fields
                formatted.put("id", conv.get("conversation_id"));
                formatted.put("created_at", ((Timestamp) conv.get("created_at")).getTime());
                formatted.put("last_message_content", conv.get("last_message_content"));
                formatted.put("last_message_time", 
                    conv.get("last_message_created_at") != null ? 
                    ((Timestamp) conv.get("last_message_created_at")).getTime() : null);
                    
                Boolean isGroup = (Boolean) conv.get("is_group");
                formatted.put("is_group", isGroup != null ? isGroup : false);
                
                if (isGroup != null && isGroup) {
                    // Group chat formatting
                    formatted.put("name", conv.get("group_name"));
                    formatted.put("participant_count", conv.get("participant_count"));
                    formatted.put("created_by", conv.get("created_by"));
                    
                    // Get participant data for group chats
                    Long conversationId = ((Number) conv.get("conversation_id")).longValue();
                    String participantSql = """
                        SELECT u.id, u.username, u.first_name, u.last_name, u.photo
                        FROM app_user u
                        JOIN dm_conversation_participant dcp ON u.id = dcp.user_id
                        WHERE dcp.conversation_id = ?
                        ORDER BY dcp.joined_at
                        LIMIT 4
                        """;
                    
                    List<Map<String, Object>> participants = jdbcTemplate.queryForList(participantSql, conversationId);
                    formatted.put("participants", participants);
                    
                    // For group compatibility with existing UI
                    formatted.put("user1_id", 0); // Not applicable for groups
                    formatted.put("user2_id", 0); // Not applicable for groups
                    formatted.put("other_user_id", null);
                    formatted.put("other_user_name", conv.get("group_name"));
                    formatted.put("other_user_photo", null);
                    formatted.put("other_first_name", null);
                    formatted.put("other_last_name", null);
                } else {
                    // 1:1 chat formatting (existing logic)
                    formatted.put("name", null);
                    formatted.put("participant_count", conv.get("participant_count"));
                    formatted.put("created_by", null);
                    formatted.put("participants", null);
                    
                    // User info
                    formatted.put("user1_id", conv.get("user1_id"));
                    formatted.put("user2_id", conv.get("user2_id"));
                    formatted.put("other_user_id", conv.get("other_user_id"));
                    formatted.put("other_user_name", 
                        (conv.get("other_first_name") != null ? conv.get("other_first_name") + " " : "") +
                        (conv.get("other_last_name") != null ? conv.get("other_last_name") : ""));
                    formatted.put("other_user_photo", conv.get("other_user_photo"));
                    formatted.put("other_first_name", conv.get("other_first_name"));
                    formatted.put("other_last_name", conv.get("other_last_name"));
                }
                
                // Calculate unread count for this conversation
                // Removed view tracking - set unread count to 0 for now
                Long conversationId = ((Number) conv.get("conversation_id")).longValue();
                
                formatted.put("unread_count", 0);
                formatted.put("has_unread_messages", false);
                
                formattedConversations.add(formatted);
            }

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
    @PostMapping(value = "/{userId}/message", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> postMessage(
            @PathVariable Long userId,
            @RequestParam("content") String content,
            @RequestParam(value = "media", required = false) MultipartFile media,
            @RequestParam(value = "mediaType", required = false) String mediaType,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "localMediaPath", required = false) String localMediaPath,
            @RequestParam("conversationId") Long conversationId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Posting DM message with media for user: {}", userId);
        
        try {
            // Debug logging for localMediaPath
            logger.debug("🎥 DM postMessage - localMediaPath received: {}", localMediaPath);
            logger.debug("🎥 DM postMessage - mediaType: {}, videoUrl: {}", mediaType, videoUrl);
            
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
            String validateSql = """
                SELECT c.id, c.is_group, c.user1_id, c.user2_id, c.name,
                       EXISTS(SELECT 1 FROM dm_conversation_participant dcp 
                              WHERE dcp.conversation_id = c.id AND dcp.user_id = ?) as is_participant
                FROM dm_conversation c 
                WHERE c.id = ?
                """;
            
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, senderId, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Boolean isGroup = (Boolean) conv.get("is_group");
            Boolean isParticipant = (Boolean) conv.get("is_participant");
            
            // Check authorization based on conversation type
            boolean authorized = false;
            if (isGroup != null && isGroup) {
                // For group chats, check participant table
                authorized = isParticipant != null && isParticipant;
            } else {
                // For 1:1 chats, check user1_id/user2_id (handle nulls safely)
                Object user1IdObj = conv.get("user1_id");
                Object user2IdObj = conv.get("user2_id");
                if (user1IdObj != null && user2IdObj != null) {
                    Long user1Id = ((Number) user1IdObj).longValue();
                    Long user2Id = ((Number) user2IdObj).longValue();
                    authorized = senderId.equals(user1Id) || senderId.equals(user2Id);
                }
            }
            
            if (!authorized) {
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
    
            // Debug what we're about to insert
            logger.debug("🎥 DM INSERT - About to store localMediaPath: {}", localMediaPath);
            logger.debug("🎥 DM INSERT - mediaUrl: {}, mediaType: {}, thumbnailUrl: {}", mediaUrl, mediaType, thumbnailUrl);
            
            // Insert the DM message and get the new ID (fixed parameter count)
            String insertSql = "INSERT INTO dm_message (conversation_id, sender_id, content, " +
                "media_url, media_type, media_thumbnail, local_media_path, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
    
            Long newMessageId = jdbcTemplate.queryForObject(insertSql, Long.class,
                conversationId,
                senderId,
                content, 
                mediaUrl,
                mediaType,
                thumbnailUrl,
                localMediaPath,
                Timestamp.valueOf(LocalDateTime.now())
            );
    
            // Determine recipients for broadcasting
            List<Long> recipientIds = new ArrayList<>();
            
            if (isGroup != null && isGroup) {
                // For group chats, get all participants except sender
                String getParticipantsSql = """
                    SELECT user_id 
                    FROM dm_conversation_participant 
                    WHERE conversation_id = ? AND user_id != ?
                    """;
                List<Map<String, Object>> participants = jdbcTemplate.queryForList(getParticipantsSql, conversationId, senderId);
                recipientIds = participants.stream()
                    .map(p -> ((Number) p.get("user_id")).longValue())
                    .collect(Collectors.toList());
            } else {
                // For 1:1 chats, send only to the other participant
                Long user1Id = conv.get("user1_id") != null ? ((Number) conv.get("user1_id")).longValue() : null;
                Long user2Id = conv.get("user2_id") != null ? ((Number) conv.get("user2_id")).longValue() : null;
                
                if (user1Id != null && user2Id != null) {
                    Long recipientId = senderId.equals(user1Id) ? user2Id : user1Id;
                    recipientIds.add(recipientId);
                }
            }

            // Fetch the full DM message with sender info (for first recipient or all participants)
            Long primaryRecipientId = recipientIds.isEmpty() ? null : recipientIds.get(0);
            
            String fetchSql = """
                SELECT 
                    dm.id, dm.conversation_id, dm.sender_id, dm.content, 
                    dm.media_url, dm.media_type, dm.media_thumbnail, 
                    dm.media_filename, dm.media_size, dm.media_duration, dm.local_media_path, dm.created_at,
                    u.username as sender_username, u.first_name as sender_first_name, 
                    u.last_name as sender_last_name, u.photo as sender_photo,
                    false as is_read
                FROM dm_message dm
                JOIN app_user u ON dm.sender_id = u.id
                -- Removed message_view table references
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
            response.put("local_media_path", messageData.get("local_media_path"));
            response.put("created_at", messageData.get("created_at"));
            
            // Add camelCase versions for Flutter compatibility
            response.put("localMediaPath", messageData.get("local_media_path"));
            response.put("mediaUrl", messageData.get("media_url"));
            response.put("mediaType", messageData.get("media_type"));
            response.put("mediaThumbnail", messageData.get("media_thumbnail"));
            
            // Debug log the response to verify localMediaPath is included
            logger.debug("🎯 DM POST Response - localMediaPath: {}", messageData.get("local_media_path"));
            logger.debug("🎯 DM POST Response - messageData: {}", messageData);
            response.put("sender_username", messageData.get("sender_username"));
            response.put("sender_first_name", messageData.get("sender_first_name"));
            response.put("sender_last_name", messageData.get("sender_last_name"));
            response.put("sender_photo", messageData.get("sender_photo"));
            response.put("is_read", messageData.get("is_read"));
    
            // Broadcast the raw database result to all recipients
            logger.debug("Broadcasting DM message to {} recipients: {}", recipientIds.size(), messageData);
            for (Long recipientId : recipientIds) {
                // Calculate unread count for this specific recipient
                String unreadCountSql = """
                    SELECT COUNT(*) FROM dm_message dm
                    JOIN dm_conversation dc ON dm.conversation_id = dc.id
                    WHERE dm.conversation_id = ? 
                    AND dm.sender_id != ?
                    AND dm.is_read = FALSE
                    AND (
                        -- For group chats: check participant table
                        (dc.is_group = TRUE AND EXISTS (
                            SELECT 1 FROM dm_conversation_participant dcp 
                            WHERE dcp.conversation_id = dc.id AND dcp.user_id = ?
                        )) OR
                        -- For 1:1 chats: check user1_id/user2_id
                        (dc.is_group = FALSE AND (dc.user1_id = ? OR dc.user2_id = ?))
                    )
                    """;
                
                Long unreadCount = jdbcTemplate.queryForObject(unreadCountSql, Long.class, 
                    conversationId, recipientId, recipientId, recipientId, recipientId);
                
                // Add unread count to the message data for this recipient
                Map<String, Object> recipientMessageData = new HashMap<>(messageData);
                recipientMessageData.put("unread_count", unreadCount != null ? unreadCount : 0);
                
                // Schedule WebSocket broadcast to happen AFTER transaction commits
                final Map<String, Object> finalRecipientMessageData = recipientMessageData;
                final Long finalRecipientId = recipientId;
                
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // Check if recipient has muted the sender
                        boolean isMuted = isRecipientMutedBySender(finalRecipientId, senderId);
                        if (!isMuted) {
                            webSocketBroadcastService.broadcastDMMessage(finalRecipientMessageData, finalRecipientId);
                            logger.debug("Broadcasted DM message to recipient {} (AFTER COMMIT)", finalRecipientId);
                        } else {
                            logger.debug("Skipped broadcasting DM message to muted recipient {} (AFTER COMMIT)", finalRecipientId);
                        }
                    }
                });
            }
            
            // Send push notification to all recipients (background notification)
            try {
                String senderName = (String) userData.get("username");
                for (Long recipientId : recipientIds) {
                    // Check if recipient has muted the sender
                    boolean isMuted = isRecipientMutedBySender(recipientId, senderId);
                    if (!isMuted) {
                        pushNotificationService.sendDMNotification(newMessageId, recipientId, senderName, content);
                        logger.debug("Sent push notification to recipient {}", recipientId);
                    } else {
                        logger.debug("Skipped push notification to muted recipient {}", recipientId);
                    }
                }
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
    // DEBUG: Track which endpoint is being hit
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

            // Validate user is part of this conversation (both 1:1 and group chats)
            String validateSql = """
                SELECT c.id, c.is_group, c.user1_id, c.user2_id,
                       EXISTS(SELECT 1 FROM dm_conversation_participant dcp 
                              WHERE dcp.conversation_id = c.id AND dcp.user_id = ?) as is_participant
                FROM dm_conversation c 
                WHERE c.id = ?
                """;
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, currentUserId, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Boolean isGroup = (Boolean) conv.get("is_group");
            Boolean isParticipant = (Boolean) conv.get("is_participant");
            
            // Check authorization based on conversation type
            boolean authorized = false;
            if (isGroup != null && isGroup) {
                // For group chats, check participant table
                authorized = isParticipant != null && isParticipant;
            } else {
                // For 1:1 chats, check user1_id/user2_id (handle nulls safely)
                Object user1IdObj = conv.get("user1_id");
                Object user2IdObj = conv.get("user2_id");
                if (user1IdObj != null && user2IdObj != null) {
                    Long user1Id = ((Number) user1IdObj).longValue();
                    Long user2Id = ((Number) user2IdObj).longValue();
                    authorized = currentUserId.equals(user1Id) || currentUserId.equals(user2Id);
                }
            }
            
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Get messages with pagination
            int offset = page * size;
            logger.debug("Getting DM messages for conversation {} for user {} (checking for muted users)", conversationId, currentUserId);
            String messagesSql = """
                SELECT 
                    m.id, m.conversation_id, m.sender_id, m.content, m.media_url, m.media_type, 
                    m.media_thumbnail, m.media_filename, m.media_size, m.media_duration, m.local_media_path,
                    m.is_read,
                    m.created_at,
                    u.username as sender_username, u.first_name as sender_first_name, u.last_name as sender_last_name,
                    u.photo as sender_photo
                FROM dm_message m
                JOIN app_user u ON m.sender_id = u.id
                -- Removed message_view table references
                WHERE m.conversation_id = ?
                AND m.sender_id NOT IN (
                    SELECT umms.member_user_id 
                    FROM user_member_message_settings umms 
                    WHERE umms.user_id = ? 
                    AND umms.receive_messages = false
                )
                ORDER BY m.created_at DESC
                LIMIT ? OFFSET ?
                """;

            List<Map<String, Object>> messages = jdbcTemplate.queryForList(messagesSql, conversationId, currentUserId, size, offset);
            
            // Process messages to add camelCase versions of fields (like main MessageController does)
            for (Map<String, Object> message : messages) {
                // Convert local_media_path to localMediaPath for Flutter compatibility
                if (message.containsKey("local_media_path")) {
                    Object localMediaPath = message.get("local_media_path");
                    message.put("localMediaPath", localMediaPath);
                 }
                
                // Convert other fields for consistency
                if (message.containsKey("media_url")) {
                    message.put("mediaUrl", message.get("media_url"));
                }
                if (message.containsKey("media_type")) {
                    message.put("mediaType", message.get("media_type"));
                }
                if (message.containsKey("media_thumbnail")) {
                    message.put("mediaThumbnail", message.get("media_thumbnail"));
                }
                
                logger.debug("DM Message data: {}", message);
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

            // Validate user is part of this conversation (both 1:1 and group chats)
            String validateSql = """
                SELECT c.id, c.is_group, c.user1_id, c.user2_id,
                       EXISTS(SELECT 1 FROM dm_conversation_participant dcp 
                              WHERE dcp.conversation_id = c.id AND dcp.user_id = ?) as is_participant
                FROM dm_conversation c 
                WHERE c.id = ?
                """;
            List<Map<String, Object>> convData = jdbcTemplate.queryForList(validateSql, currentUserId, conversationId);
            if (convData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
            }

            Map<String, Object> conv = convData.get(0);
            Boolean isGroup = (Boolean) conv.get("is_group");
            Boolean isParticipant = (Boolean) conv.get("is_participant");
            
            // Check authorization based on conversation type
            boolean authorized = false;
            if (isGroup != null && isGroup) {
                // For group chats, check participant table
                authorized = isParticipant != null && isParticipant;
            } else {
                // For 1:1 chats, check user1_id/user2_id (handle nulls safely)
                Object user1IdObj = conv.get("user1_id");
                Object user2IdObj = conv.get("user2_id");
                if (user1IdObj != null && user2IdObj != null) {
                    Long user1Id = ((Number) user1IdObj).longValue();
                    Long user2Id = ((Number) user2IdObj).longValue();
                    authorized = currentUserId.equals(user1Id) || currentUserId.equals(user2Id);
                }
            }
            
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized for this conversation"));
            }

            // Mark all unread messages from other users in this conversation as read
            String markReadSql = """
                UPDATE dm_message 
                SET is_read = TRUE 
                WHERE conversation_id = ? 
                AND sender_id != ? 
                AND is_read = FALSE
                """;
            
            int markedCount = jdbcTemplate.update(markReadSql, conversationId, currentUserId);
            logger.debug("Marked {} messages as read for user {} in conversation {}", markedCount, currentUserId, conversationId);
            
            return ResponseEntity.ok(Map.of("markedAsRead", markedCount));

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

    /**
     * Create a new group chat
     * POST /api/dm/groups
     */
    @PostMapping("/groups")
    @Transactional
    public ResponseEntity<Map<String, Object>> createGroupChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> requestBody) {
        logger.debug("Creating new group chat");
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Extract request data
            String groupName = (String) requestBody.get("name");
            @SuppressWarnings("unchecked")
            List<Integer> participantIds = (List<Integer>) requestBody.get("participantIds");
            
            // Validate participants
            if (participantIds == null || participantIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "At least one participant is required"));
            }
            
            // Enforce configurable participant limit (including creator)
            if (participantIds.size() >= maxGroupChatParticipants) {
                return ResponseEntity.badRequest().body(Map.of("error", 
                    "Maximum " + (maxGroupChatParticipants - 1) + " participants allowed (" + maxGroupChatParticipants + " total including you)"));
            }
            
            // Convert to Long and add creator
            List<Long> allParticipantIds = new ArrayList<>();
            allParticipantIds.add(currentUserId); // Add creator first
            for (Integer id : participantIds) {
                Long participantId = id.longValue();
                if (!participantId.equals(currentUserId)) { // Don't add creator twice
                    allParticipantIds.add(participantId);
                }
            }
            
            // Validate all participants exist
            String placeholders = allParticipantIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
            String validateSql = "SELECT COUNT(*) FROM app_user WHERE id IN (" + placeholders + ")";
            
            Long validUserCount = jdbcTemplate.queryForObject(validateSql, Long.class, 
                allParticipantIds.toArray());
            
            if (validUserCount != allParticipantIds.size()) {
                return ResponseEntity.badRequest().body(Map.of("error", "One or more participants not found"));
            }
            
            // Create group conversation
            String insertGroupSql = """
                INSERT INTO dm_conversation (name, is_group, created_by_user_id, created_at) 
                VALUES (?, TRUE, ?, ?) RETURNING id, created_at
                """;
            
            Map<String, Object> groupConversation = jdbcTemplate.queryForMap(insertGroupSql, 
                groupName, currentUserId, Timestamp.valueOf(LocalDateTime.now()));
            
            Long conversationId = ((Number) groupConversation.get("id")).longValue();
            logger.debug("Created group conversation: {}", conversationId);
            
            // Add all participants
            String insertParticipantSql = """
                INSERT INTO dm_conversation_participant (conversation_id, user_id, joined_at) 
                VALUES (?, ?, ?)
                """;
            
            for (Long participantId : allParticipantIds) {
                jdbcTemplate.update(insertParticipantSql, conversationId, participantId, 
                    Timestamp.valueOf(LocalDateTime.now()));
            }
            
            // Get participant info for response
            String participantInfoSql = """
                SELECT u.id, u.username, u.first_name, u.last_name, u.photo
                FROM app_user u
                JOIN dm_conversation_participant dcp ON u.id = dcp.user_id
                WHERE dcp.conversation_id = ?
                ORDER BY dcp.joined_at
                """;
            
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(participantInfoSql, conversationId);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("id", conversationId);
            response.put("name", groupName);
            response.put("isGroup", true);
            response.put("participants", participants);
            response.put("createdBy", currentUserId);
            response.put("createdAt", groupConversation.get("created_at"));
            
            logger.debug("Successfully created group chat with {} participants", participants.size());
            
            // Broadcast the new conversation to all participants so their conversation lists refresh
            for (Long participantId : allParticipantIds) {
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("type", "new_conversation");
                notificationData.put("conversationId", conversationId);
                notificationData.put("conversationName", groupName);
                notificationData.put("isGroup", true);
                notificationData.put("participantCount", allParticipantIds.size());
                
                // Send to the conversation list topic for each participant
                String destination = "/topic/dm-list/" + participantId;
                webSocketBroadcastService.broadcastDMMessage(notificationData, participantId);
                logger.debug("Broadcasted new group conversation notification to user: {}", participantId);
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error creating group chat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create group chat: " + e.getMessage()));
        }
    }

    /**
     * Add participants to a group chat
     * POST /api/dm/groups/{conversationId}/participants
     */
    @PostMapping("/groups/{conversationId}/participants")
    @Transactional
    public ResponseEntity<Map<String, Object>> addGroupParticipants(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId,
            @RequestBody Map<String, Object> requestBody) {
        logger.debug("Adding participants to group {}", conversationId);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Verify user is in the group
            String checkMemberSql = "SELECT COUNT(*) FROM dm_conversation_participant WHERE conversation_id = ? AND user_id = ?";
            Long memberCount = jdbcTemplate.queryForObject(checkMemberSql, Long.class, conversationId, currentUserId);
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not a member of this group"));
            }

            // Extract participant IDs
            @SuppressWarnings("unchecked")
            List<Integer> participantIds = (List<Integer>) requestBody.get("participantIds");
            
            if (participantIds == null || participantIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No participants specified"));
            }

            // Check configurable group size limit
            String currentSizeSql = "SELECT COUNT(*) FROM dm_conversation_participant WHERE conversation_id = ?";
            Long currentSize = jdbcTemplate.queryForObject(currentSizeSql, Long.class, conversationId);
            if (currentSize + participantIds.size() > maxGroupChatParticipants) {
                return ResponseEntity.badRequest().body(Map.of("error", 
                    "Group size limit exceeded (max " + maxGroupChatParticipants + " members)"));
            }

            // Add new participants
            String insertParticipantSql = "INSERT INTO dm_conversation_participant (conversation_id, user_id, joined_at) VALUES (?, ?, ?) ON CONFLICT (conversation_id, user_id) DO NOTHING";
            
            List<Map<String, Object>> addedParticipants = new ArrayList<>();
            for (Integer participantId : participantIds) {
                Long longParticipantId = participantId.longValue();
                
                jdbcTemplate.update(insertParticipantSql, conversationId, longParticipantId, 
                    Timestamp.valueOf(LocalDateTime.now()));
                
                // Get participant info
                String userInfoSql = "SELECT id, username, first_name, last_name, photo FROM app_user WHERE id = ?";
                try {
                    Map<String, Object> participantInfo = jdbcTemplate.queryForMap(userInfoSql, longParticipantId);
                    addedParticipants.add(participantInfo);
                } catch (Exception e) {
                    logger.warn("Could not fetch info for participant {}: {}", longParticipantId, e.getMessage());
                }
            }

            logger.debug("Successfully added {} participants to group {}", addedParticipants.size(), conversationId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Participants added successfully",
                "addedParticipants", addedParticipants
            ));
            
        } catch (Exception e) {
            logger.error("Error adding group participants: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to add participants: " + e.getMessage()));
        }
    }

    /**
     * Remove a participant from a group chat
     * DELETE /api/dm/groups/{conversationId}/participants/{participantId}
     */
    @DeleteMapping("/groups/{conversationId}/participants/{participantId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeGroupParticipant(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId,
            @PathVariable Long participantId) {
        logger.debug("Removing participant {} from group {}", participantId, conversationId);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Verify user is in the group
            String checkMemberSql = "SELECT COUNT(*) FROM dm_conversation_participant WHERE conversation_id = ? AND user_id = ?";
            Long memberCount = jdbcTemplate.queryForObject(checkMemberSql, Long.class, conversationId, currentUserId);
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not a member of this group"));
            }

            // Users can remove themselves, or the group creator can remove others
            String groupInfoSql = "SELECT created_by_user_id FROM dm_conversation WHERE id = ? AND is_group = true";
            Map<String, Object> groupInfo = jdbcTemplate.queryForMap(groupInfoSql, conversationId);
            Long createdBy = ((Number) groupInfo.get("created_by_user_id")).longValue();
            
            if (!currentUserId.equals(participantId) && !currentUserId.equals(createdBy)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only group creator can remove other members"));
            }

            // Don't allow removing the group creator (they must leave voluntarily)
            if (participantId.equals(createdBy) && !currentUserId.equals(createdBy)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot remove group creator"));
            }

            // Remove the participant
            String removeParticipantSql = "DELETE FROM dm_conversation_participant WHERE conversation_id = ? AND user_id = ?";
            int rowsAffected = jdbcTemplate.update(removeParticipantSql, conversationId, participantId);
            
            if (rowsAffected == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Participant not found in group"));
            }

            // If this was the last member, delete the conversation
            String remainingMembersSql = "SELECT COUNT(*) FROM dm_conversation_participant WHERE conversation_id = ?";
            Long remainingMembers = jdbcTemplate.queryForObject(remainingMembersSql, Long.class, conversationId);
            
            if (remainingMembers <= 1) {
                // Delete the conversation if only 1 or 0 members remain
                String deleteConversationSql = "DELETE FROM dm_conversation WHERE id = ?";
                jdbcTemplate.update(deleteConversationSql, conversationId);
                logger.debug("Deleted empty group conversation {}", conversationId);
            }

            logger.debug("Successfully removed participant {} from group {}", participantId, conversationId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Participant removed successfully",
                "removedParticipantId", participantId,
                "remainingMembers", Math.max(0, remainingMembers - 1)
            ));
            
        } catch (Exception e) {
            logger.error("Error removing group participant: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to remove participant: " + e.getMessage()));
        }
    }

    /**
     * Search DM conversations and messages
     * GET /api/dm/search?q={query}
     */
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> searchDMConversations(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.debug("Searching DM conversations with query: {}", query);
        
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Search query is required"));
            }

            String searchTerm = "%" + query.toLowerCase() + "%";
            
            // Search across conversations, group names, participant names, and message content
            String searchSql = """
                SELECT DISTINCT 
                    c.id as conversation_id,
                    c.is_group,
                    c.name as group_name,
                    c.created_by_user_id,
                    c.user1_id,
                    c.user2_id,
                    c.created_at,
                    u.id as other_user_id,
                    u.username as other_username,
                    u.first_name as other_first_name,
                    u.last_name as other_last_name,
                    u.photo as other_user_photo,
                    m.content as last_message_content,
                    m.created_at as last_message_created_at,
                    CASE 
                        WHEN c.is_group = TRUE THEN (
                            SELECT COUNT(*) 
                            FROM dm_conversation_participant dcp 
                            WHERE dcp.conversation_id = c.id
                        )
                        ELSE 2
                    END as participant_count,
                    COALESCE(m.created_at, c.created_at) as sort_date
                FROM dm_conversation c
                -- Left join with participants to include conversations user belongs to
                LEFT JOIN dm_conversation_participant my_participation ON c.id = my_participation.conversation_id AND my_participation.user_id = ?
                -- For 1:1 chats, get the other user info
                LEFT JOIN app_user u ON (
                    c.is_group = FALSE AND (
                        (c.user1_id = ? AND u.id = c.user2_id) OR 
                        (c.user2_id = ? AND u.id = c.user1_id)
                    )
                )
                -- Get latest message for preview
                LEFT JOIN (
                    SELECT DISTINCT ON (conversation_id) 
                        conversation_id, content, created_at
                    FROM dm_message
                    ORDER BY conversation_id, created_at DESC
                ) m ON m.conversation_id = c.id
                WHERE (
                    -- Include group conversations where user is a participant
                    (c.is_group = TRUE AND my_participation.user_id IS NOT NULL) OR
                    -- Include 1:1 conversations where user is user1 or user2
                    (c.is_group = FALSE AND (c.user1_id = ? OR c.user2_id = ?))
                ) AND (
                    -- Search group names
                    LOWER(c.name) LIKE ? OR
                    -- Search 1:1 user names (only for 1:1 chats where u is not null)
                    (c.is_group = FALSE AND (
                        LOWER(u.first_name) LIKE ? OR
                        LOWER(u.last_name) LIKE ? OR
                        LOWER(u.username) LIKE ? OR
                        LOWER(CONCAT(u.first_name, ' ', u.last_name)) LIKE ?
                    )) OR
                    -- Search message content
                    EXISTS (
                        SELECT 1 FROM dm_message dm 
                        WHERE dm.conversation_id = c.id 
                        AND LOWER(dm.content) LIKE ?
                    ) OR
                    -- Search group participant names
                    (c.is_group = TRUE AND EXISTS (
                        SELECT 1 FROM dm_conversation_participant dcp2
                        JOIN app_user u2 ON dcp2.user_id = u2.id
                        WHERE dcp2.conversation_id = c.id
                        AND (
                            LOWER(u2.first_name) LIKE ? OR
                            LOWER(u2.last_name) LIKE ? OR
                            LOWER(u2.username) LIKE ? OR
                            LOWER(CONCAT(u2.first_name, ' ', u2.last_name)) LIKE ?
                        )
                    ))
                )
                ORDER BY sort_date DESC
                LIMIT ? OFFSET ?
                """;

            int offset = page * size;
            List<Map<String, Object>> results = jdbcTemplate.queryForList(searchSql, 
                currentUserId, currentUserId, currentUserId, currentUserId, currentUserId,
                searchTerm, searchTerm, searchTerm, searchTerm, searchTerm, searchTerm,
                searchTerm, searchTerm, searchTerm, searchTerm,
                size, offset);

            // Format results like the regular conversation list
            List<Map<String, Object>> formattedResults = new ArrayList<>();
            for (Map<String, Object> conv : results) {
                Map<String, Object> formatted = new HashMap<>();
                formatted.put("id", conv.get("conversation_id")); // Fix: use "id" not "conversation_id"
                formatted.put("is_group", conv.get("is_group"));
                formatted.put("created_at", conv.get("created_at"));
                formatted.put("updated_at", conv.get("created_at")); // Add missing updated_at field
                formatted.put("last_message_content", conv.get("last_message_content"));
                formatted.put("last_message_time", conv.get("last_message_created_at"));

                Boolean isGroup = (Boolean) conv.get("is_group");
                if (isGroup != null && isGroup) {
                    // Group chat formatting
                    formatted.put("name", conv.get("group_name"));
                    formatted.put("participant_count", conv.get("participant_count"));
                    formatted.put("created_by", conv.get("created_by_user_id"));
                    
                    // Get participant data for group chats
                    Long conversationId = ((Number) conv.get("conversation_id")).longValue();
                    String participantSql = """
                        SELECT u.id, u.username, u.first_name, u.last_name, u.photo
                        FROM app_user u
                        JOIN dm_conversation_participant dcp ON u.id = dcp.user_id
                        WHERE dcp.conversation_id = ?
                        ORDER BY dcp.joined_at
                        LIMIT 4
                        """;
                    
                    List<Map<String, Object>> participants = jdbcTemplate.queryForList(participantSql, conversationId);
                    formatted.put("participants", participants);
                    
                    // For group compatibility
                    formatted.put("user1_id", 0);
                    formatted.put("user2_id", 0);
                    formatted.put("other_user_id", null);
                    formatted.put("other_user_name", conv.get("group_name"));
                    formatted.put("other_user_photo", null);
                    formatted.put("other_first_name", null);
                    formatted.put("other_last_name", null);
                } else {
                    // 1:1 chat formatting
                    formatted.put("name", null);
                    formatted.put("participant_count", 2);
                    formatted.put("created_by", null);
                    formatted.put("participants", null);
                    
                    // User info
                    formatted.put("user1_id", conv.get("user1_id"));
                    formatted.put("user2_id", conv.get("user2_id"));
                    formatted.put("other_user_id", conv.get("other_user_id"));
                    formatted.put("other_user_name", 
                        (conv.get("other_first_name") != null ? conv.get("other_first_name") + " " : "") +
                        (conv.get("other_last_name") != null ? conv.get("other_last_name") : ""));
                    formatted.put("other_user_photo", conv.get("other_user_photo"));
                    formatted.put("other_first_name", conv.get("other_first_name"));
                    formatted.put("other_last_name", conv.get("other_last_name"));
                }
                
                // Calculate unread count (simplified for search results)
                formatted.put("unread_count", 0);
                formatted.put("has_unread_messages", false);
                
                formattedResults.add(formatted);
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("conversations", formattedResults);
            response.put("total_results", formattedResults.size());
            response.put("page", page);
            response.put("size", size);
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching DM conversations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to search conversations: " + e.getMessage()));
        }
    }
} 