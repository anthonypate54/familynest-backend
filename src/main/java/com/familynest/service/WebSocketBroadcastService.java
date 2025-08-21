package com.familynest.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.familynest.repository.UserFamilyMembershipRepository;

import java.util.Map;
import java.util.List;

@Service
public class WebSocketBroadcastService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcastService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final UserFamilyMembershipRepository userFamilyMembershipRepository;
    private final JdbcTemplate jdbcTemplate;

    public WebSocketBroadcastService(
            SimpMessagingTemplate messagingTemplate,
            UserFamilyMembershipRepository userFamilyMembershipRepository,
            JdbcTemplate jdbcTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.userFamilyMembershipRepository = userFamilyMembershipRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Check if a recipient has muted the sender
     */
    private boolean isRecipientMutedBySender(Long recipientId, Long senderId) {
        if (senderId == null || recipientId == null) {
            return false; // Can't be muted if we don't have IDs
        }
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
    
    /**
     * Extract sender ID from message data
     */
    private Long getSenderIdFromMessage(Map<String, Object> messageData) {
        try {
            Object senderIdObj = messageData.get("sender_id");
            if (senderIdObj instanceof Number) {
                return ((Number) senderIdObj).longValue();
            }
            return null;
        } catch (Exception e) {
            logger.error("Error extracting sender_id from message data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Broadcast DM MESSAGE to a specific recipient
     */
    public void broadcastDMMessage(Map<String, Object> messageData, Long recipientId) {
        try {
            messageData.put("type", "DM_MESSAGE");
            messageData.put("recipient_id", recipientId);
            
            logger.debug("Broadcasting DM MESSAGE to recipient {}", recipientId);
            
            // Send to conversation list topic (for MessagesHomeScreen)
            String listDestination = "/topic/dm-list/" + recipientId;
            messagingTemplate.convertAndSend(listDestination, messageData);
            logger.debug("Successfully broadcast DM MESSAGE to conversation list {}", listDestination);
            
            // Send to thread topic (for DMThreadScreen) 
            String threadDestination = "/topic/dm-thread/" + recipientId;
            messagingTemplate.convertAndSend(threadDestination, messageData);
            logger.debug("Successfully broadcast DM MESSAGE to thread {}", threadDestination);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast DM message to recipient {}: {}", recipientId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    /**
     * Broadcast family message to all family members using user-specific topics.
     * This is the new improved architecture that eliminates the need for clients
     * to subscribe to multiple family topics.
     */
    public void broadcastFamilyMessage(Map<String, Object> messageData, Long familyId) {
        try {
            // Add family_id to the message data
            messageData.put("family_id", familyId);
            
            logger.debug("Broadcasting family message to family {} members", familyId);
            
            // Get all family members
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            logger.debug("Found {} members in family {}", familyMemberIds.size(), familyId);
            
            // Broadcast to each family member's individual topic (excluding muted recipients)
            int broadcastCount = 0;
            Long senderId = getSenderIdFromMessage(messageData);
            logger.debug("🔇 WebSocket: Extracted senderId={} from message data for family {}", senderId, familyId);
            
            for (Long userId : familyMemberIds) {
                try {
                    // Check if this user has muted the sender
                    boolean isMuted = isRecipientMutedBySender(userId, senderId);
                    logger.debug("🔇 WebSocket: User {} mute check for sender {}: isMuted={}", userId, senderId, isMuted);
                    
                    if (!isMuted) {
                        String userDestination = "/user/" + userId + "/family";
                        messagingTemplate.convertAndSend(userDestination, messageData);
                        broadcastCount++;
                        logger.debug("✅ Broadcast family message to user {}", userId);
                    } else {
                        logger.debug("🔇 Skipped broadcasting family message to muted user {}", userId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to broadcast to user {}: {}", userId, e.getMessage());
                    // Continue to other users even if one fails
                }
            }
            
            logger.debug("Successfully broadcast family message to {}/{} family members", 
                broadcastCount, familyMemberIds.size());
            
            // Also broadcast to the legacy family topic for backward compatibility
            // This can be removed once all clients are updated
            String legacyDestination = "/family/" + familyId;
            try {
                messagingTemplate.convertAndSend(legacyDestination, messageData);
                logger.debug("Also broadcast to legacy destination: {}", legacyDestination);
            } catch (Exception e) {
                logger.warn("Failed to broadcast to legacy destination {}: {}", legacyDestination, e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to broadcast family message to family {}: {}", familyId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use broadcastFamilyMessage instead which handles user-specific broadcasting
     */
    @Deprecated
    public void broadcastFamilyMessageLegacy(Map<String, Object> messageData, Long familyId) {
        try {
            // Add family_id to the message data
            messageData.put("family_id", familyId);
            String destination = "/family/" + familyId;
            
            logger.debug("Broadcasting family message to legacy destination: {}", destination);
            messagingTemplate.convertAndSend(destination, messageData);
            logger.debug("Successfully broadcast family message to {}", destination);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast family message to family {}: {}", familyId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    /**
     * Broadcast NEW MESSAGE to family members (main message feed)
     */
    public void broadcastNewMessage(Map<String, Object> messageData, Long familyId) {
        try {
            messageData.put("type", "NEW_MESSAGE");
            messageData.put("family_id", familyId);
            
            logger.debug("Broadcasting NEW MESSAGE to family {} members", familyId);
            
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                // Check if this user has muted the message sender (using member message settings)
                Long senderId = ((Number) messageData.get("sender_id")).longValue();
                boolean userHasMutedSender = isRecipientMutedBySender(userId, senderId);
                logger.debug("🔇 NEW_MESSAGE: User {} mute check for sender {}: isMuted={}", userId, senderId, userHasMutedSender);
                
                if (userHasMutedSender) {
                    logger.debug("🔇 Skipping user {} - has muted sender {}", userId, senderId);
                    continue;
                }
                
                try {
                    String destination = "/user/" + userId + "/messages";
                    messagingTemplate.convertAndSend(destination, messageData);
                    broadcastCount++;
                    logger.debug("Broadcast NEW MESSAGE to user {}", userId);
                } catch (Exception e) {
                    logger.error("Failed to broadcast new message to user {}: {}", userId, e.getMessage());
                }
            }
            
            logger.debug("Successfully broadcast NEW MESSAGE to {}/{} family members", 
                broadcastCount, familyMemberIds.size());
            
        } catch (Exception e) {
            logger.error("Failed to broadcast new message to family {}: {}", familyId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast COMMENT to users viewing a specific thread
     */
    public void broadcastComment(Map<String, Object> commentData, Long parentMessageId, Long familyId) {
        try {
            commentData.put("type", "COMMENT");
            commentData.put("parentMessageId", parentMessageId);
            commentData.put("family_id", familyId);
            
            logger.debug("Broadcasting COMMENT for thread {} to family {} members", parentMessageId, familyId);
            
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                try {
                    String destination = "/user/" + userId + "/comments/" + parentMessageId;
                    messagingTemplate.convertAndSend(destination, commentData);
                    broadcastCount++;
                    logger.debug("Broadcast COMMENT to user {} for thread {}", userId, parentMessageId);
                } catch (Exception e) {
                    logger.error("Failed to broadcast comment to user {}: {}", userId, e.getMessage());
                }
            }
            
            logger.debug("Successfully broadcast COMMENT to {}/{} family members", 
                broadcastCount, familyMemberIds.size());
            
        } catch (Exception e) {
            logger.error("Failed to broadcast comment for thread {}: {}", parentMessageId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast REACTION (like/love) to family members
     */
    public void broadcastReaction(Map<String, Object> reactionData, Long messageId, Long familyId) {
        try {
            reactionData.put("type", "REACTION");
            reactionData.put("messageId", messageId);
            reactionData.put("family_id", familyId);
            
            logger.debug("Broadcasting REACTION for message {} to family {} members", messageId, familyId);
            
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                // Check if user has muted this family using matrix table
                String familyMuteSql = """
                    SELECT COUNT(*) FROM user_notification_matrix 
                    WHERE user_id = ? 
                    AND family_id = ? 
                    AND member_id = 0 
                    AND reactions_websocket = FALSE
                """;
                Integer muteCount = jdbcTemplate.queryForObject(familyMuteSql, Integer.class, userId, familyId);
                boolean userHasMutedFamily = muteCount != null && muteCount > 0;
                
                if (userHasMutedFamily) {
                    logger.debug("Skipping muted user {} for reaction", userId);
                    continue;
                }
                
                try {
                    String destination = "/user/" + userId + "/reactions";
                    messagingTemplate.convertAndSend(destination, reactionData);
                    broadcastCount++;
                    logger.debug("Broadcast REACTION to user {}", userId);
                } catch (Exception e) {
                    logger.error("Failed to broadcast reaction to user {}: {}", userId, e.getMessage());
                }
            }
            
            logger.debug("Successfully broadcast REACTION to {}/{} family members", 
                broadcastCount, familyMemberIds.size());
            
        } catch (Exception e) {
            logger.error("Failed to broadcast reaction for message {}: {}", messageId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast COMMENT COUNT update to family members
     */
    public void broadcastCommentCount(Long messageId, int commentCount, Long familyId) {
        try {
            Map<String, Object> commentCountData = Map.of(
                "type", "COMMENT_COUNT",
                "messageId", messageId,
                "commentCount", commentCount,
                "family_id", familyId
            );
            
            logger.debug("Broadcasting COMMENT COUNT for message {} to family {} members", messageId, familyId);
            
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            logger.debug("Found {} family members for family {}: {}", familyMemberIds.size(), familyId, familyMemberIds);
            
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                // Check if user has muted this family using matrix table
                String familyMuteSql = """
                    SELECT COUNT(*) FROM user_notification_matrix 
                    WHERE user_id = ? 
                    AND family_id = ? 
                    AND member_id = 0 
                    AND comments_websocket = FALSE
                """;
                Integer muteCount = jdbcTemplate.queryForObject(familyMuteSql, Integer.class, userId, familyId);
                boolean userHasMutedFamily = muteCount != null && muteCount > 0;
                
                if (userHasMutedFamily) {
                    logger.debug("Skipping muted user {} for comment count", userId);
                    continue;
                }
                
                try {
                    String destination = "/user/" + userId + "/comment-counts";
                    messagingTemplate.convertAndSend(destination, commentCountData);
                    broadcastCount++;
                    logger.debug("Broadcast COMMENT COUNT to user {} at destination {}", userId, destination);
                } catch (Exception e) {
                    logger.error("Failed to broadcast comment count to user {}: {}", userId, e.getMessage());
                }
            }
            
            logger.debug("Successfully broadcast COMMENT COUNT to {}/{} family members for family {}", 
                broadcastCount, familyMemberIds.size(), familyId);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast comment count for message {}: {}", messageId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast COMMENT COUNT update to family members excluding a specific user
     */
    public void broadcastCommentCountExcludingUser(Long messageId, int commentCount, Long familyId, Long excludeUserId) {
        try {
            logger.debug("Broadcasting COMMENT COUNT for message {} to family {} members excluding user {}", 
                messageId, familyId, excludeUserId);
            
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .filter(userId -> !userId.equals(excludeUserId))  // Exclude the specified user
                .toList();
            
            logger.debug("Found {} family members for family {} (excluding user {}): {}", 
                familyMemberIds.size(), familyId, excludeUserId, familyMemberIds);
            
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                // Check if user has muted this family using matrix table
                String familyMuteSql = """
                    SELECT COUNT(*) FROM user_notification_matrix 
                    WHERE user_id = ? 
                    AND family_id = ? 
                    AND member_id = 0 
                    AND comments_websocket = FALSE
                """;
                Integer muteCount = jdbcTemplate.queryForObject(familyMuteSql, Integer.class, userId, familyId);
                boolean userHasMutedFamily = muteCount != null && muteCount > 0;
                
                if (userHasMutedFamily) {
                    logger.debug("Skipping muted user {} for comment count", userId);
                    continue;
                }
                
                try {
                    String destination = "/user/" + userId + "/comment-counts";
                    // Query user's read status for this message
                    String readStatusSql = "SELECT has_unread_comments FROM user_message_read " +
                                         "WHERE user_id = ? AND message_id = ?";
                    Boolean hasUnreadComments;
                    try {
                        hasUnreadComments = jdbcTemplate.queryForObject(readStatusSql, Boolean.class, userId, messageId);
                    } catch (Exception e) {
                        // If no record exists, default to true (unread) since there are comments
                        hasUnreadComments = true;
                    }
                    
                    // Create personalized payload for this user
                    Map<String, Object> personalizedData = Map.of(
                        "type", "COMMENT_COUNT",
                        "messageId", messageId,
                        "commentCount", commentCount,
                        "family_id", familyId,
                        "has_unread_comments", hasUnreadComments
                    );
                    
                    messagingTemplate.convertAndSend(destination, personalizedData);
                    broadcastCount++;
                    logger.debug("Broadcast COMMENT COUNT to user {} at destination {} (has_unread_comments: {})", 
                               userId, destination, hasUnreadComments);
                } catch (Exception e) {
                    logger.error("Failed to broadcast comment count to user {}: {}", userId, e.getMessage());
                }
            }
            
            logger.debug("Successfully broadcast COMMENT COUNT to {}/{} family members for family {} (excluding user {})", 
                broadcastCount, familyMemberIds.size(), familyId, excludeUserId);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast comment count for message {}: {}", messageId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast INVITATION to a specific user
     */
    public void broadcastInvitation(Map<String, Object> invitationData, Long userId) {
        try {
            logger.info("*** BROADCAST_INVITATION: Starting broadcast to user {} ***", userId);
            logger.info("*** BROADCAST_INVITATION: Data: {} ***", invitationData);
            
            String destination = "/user/" + userId + "/invitations";
            logger.info("*** BROADCAST_INVITATION: Destination: {} ***", destination);
            
            messagingTemplate.convertAndSend(destination, invitationData);
            logger.info("*** BROADCAST_INVITATION: Successfully sent to {} ***", destination);
            
        } catch (Exception e) {
            logger.error("*** BROADCAST_INVITATION: FAILED to user {}: {} ***", userId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    // Add more broadcast methods as needed
}
