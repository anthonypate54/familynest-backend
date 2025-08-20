package com.familynest.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Send push notification for a new family message
     */
    public void sendFamilyMessageNotification(Long messageId, Long familyId, String senderName, String messageContent) {
        try {
            logger.debug("Sending family message notification - messageId: {}, familyId: {}", messageId, familyId);
            
            // Get family members who should receive notifications
            List<Map<String, Object>> recipients = getNotificationRecipients(familyId, messageId);
            
            if (recipients.isEmpty()) {
                logger.debug("No recipients found for family message notification");
                return;
            }
            
            // Create notification payload
            String title = "New message from " + senderName;
            String body = truncateMessage(messageContent);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "FAMILY_MESSAGE");
            data.put("messageId", messageId.toString());
            data.put("familyId", familyId.toString());
            data.put("senderId", getSenderId(messageId).toString());
            
            // Send to all recipients
            sendToMultipleDevices(recipients, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending family message notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send push notification for a new DM message
     */
    public void sendDMNotification(Long messageId, Long recipientId, String senderName, String messageContent) {
        try {
            logger.debug("Sending DM notification - messageId: {}, recipientId: {}", messageId, recipientId);
            
            // Check if recipient has notifications enabled
            if (!hasNotificationsEnabled(recipientId)) {
                logger.debug("Recipient {} has notifications disabled", recipientId);
                return;
            }
            
            // Get recipient's FCM token
            String fcmToken = getFcmToken(recipientId);
            if (fcmToken == null) {
                logger.debug("No FCM token found for user {}", recipientId);
                return;
            }
            
            // Create notification payload
            String title = "New message from " + senderName;
            String body = truncateMessage(messageContent);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "DM_MESSAGE");
            data.put("messageId", messageId.toString());
            data.put("senderId", getSenderId(messageId).toString());
            data.put("recipientId", recipientId.toString());
            
            // Send notification
            sendToDevice(fcmToken, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending DM notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send push notification for a comment on a message
     */
    public void sendCommentNotification(Long commentId, Long parentMessageId, Long familyId, String commenterName, String commentContent) {
        try {
            logger.debug("Sending comment notification - commentId: {}, parentMessageId: {}", commentId, parentMessageId);
            
            // Get the original message author (they should be notified about comments)
            Long originalAuthorId = getMessageAuthor(parentMessageId);
            if (originalAuthorId == null) {
                logger.debug("Could not find original message author for message {}", parentMessageId);
                return;
            }
            
            // Check if author has notifications enabled
            if (!hasNotificationsEnabled(originalAuthorId)) {
                logger.debug("Original author {} has notifications disabled", originalAuthorId);
                return;
            }
            
            // Get author's FCM token
            String fcmToken = getFcmToken(originalAuthorId);
            if (fcmToken == null) {
                logger.debug("No FCM token found for user {}", originalAuthorId);
                return;
            }
            
            // Create notification payload
            String title = commenterName + " commented on your message";
            String body = truncateMessage(commentContent);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "COMMENT");
            data.put("commentId", commentId.toString());
            data.put("parentMessageId", parentMessageId.toString());
            data.put("familyId", familyId.toString());
            
            // Send notification
            sendToDevice(fcmToken, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending comment notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send push notification to all thread participants (message author + all commenters)
     */
    public void sendThreadParticipantNotifications(Long commentId, Long parentMessageId, Long familyId, String commenterName, String commentContent, Long commenterId) {
        try {
            logger.debug("Sending thread participant notifications - commentId: {}, parentMessageId: {}", commentId, parentMessageId);
            
            // Get all users who have participated in this thread
            List<Map<String, Object>> participants = getThreadParticipants(parentMessageId, commenterId);
            
            if (participants.isEmpty()) {
                logger.debug("No thread participants found for message {}", parentMessageId);
                return;
            }
            
            // Create notification payload
            String title = commenterName + " commented on a thread you're in";
            String body = truncateMessage(commentContent);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "COMMENT");
            data.put("commentId", commentId.toString());
            data.put("parentMessageId", parentMessageId.toString());
            data.put("familyId", familyId.toString());
            data.put("senderId", commenterId.toString());
            
            // Send to all participants
            sendToMultipleDevices(participants, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending thread participant notifications: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get all users who have participated in a thread (message author + all commenters)
     * Excludes the current commenter and respects notification preferences
     */
    private List<Map<String, Object>> getThreadParticipants(Long parentMessageId, Long currentCommenterId) {
        String sql = """
            SELECT DISTINCT u.id as user_id, u.fcm_token, u.username
            FROM (
                -- Original message author
                SELECT sender_id as user_id FROM message WHERE id = ?
                UNION
                -- All commenters on this message
                SELECT sender_id as user_id FROM message_comment WHERE parent_message_id = ?
            ) thread_users
            JOIN app_user u ON thread_users.user_id = u.id
            JOIN user_notification_matrix unm ON u.id = unm.user_id
            WHERE u.id != ?  -- Exclude current commenter
            AND u.fcm_token IS NOT NULL
            AND unm.push_enabled = TRUE
            AND unm.device_permission_granted = TRUE
            AND (
                -- Check global settings (family_id=0) - comment notifications enabled
                (unm.family_id = 0 AND unm.member_id = 0 AND unm.family_messages_push = TRUE)
            )
        """;
        
        List<Map<String, Object>> participants = jdbcTemplate.queryForList(sql, parentMessageId, parentMessageId, currentCommenterId);
        logger.debug("Found {} thread participants for message {} (excluding commenter {})", participants.size(), parentMessageId, currentCommenterId);
        
        for (Map<String, Object> participant : participants) {
            String fcmToken = (String) participant.get("fcm_token");
            String tokenPrefix = fcmToken != null && fcmToken.length() > 30 ? fcmToken.substring(0, 30) + "..." : fcmToken;
            logger.debug("Thread participant: userId={}, username={}, fcmToken={}", participant.get("user_id"), participant.get("username"), tokenPrefix);
        }
        
        return participants;
    }

    /**
     * Send push notification for a new family member
     */
    public void sendNewMemberNotification(Long familyId, String newMemberName, String familyName) {
        try {
            logger.debug("Sending new member notification for {} joining family {}", newMemberName, familyName);
            
            // Get all family members who have new member notifications enabled
            List<Map<String, Object>> recipients = getNewMemberNotificationRecipients(familyId);
            logger.debug("Found {} recipients for new member notification", recipients.size());
            
            if (recipients.isEmpty()) {
                logger.debug("No recipients found for new member notification in family {}", familyId);
                return;
            }
            
            String title = "New Family Member";
            String body = String.format("%s joined %s", newMemberName, familyName);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "NEW_MEMBER");
            data.put("familyId", familyId.toString());
            data.put("newMemberName", newMemberName);
            
            // Send notification to all recipients
            sendToMultipleDevices(recipients, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending new member notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send push notification for a family invitation
     */
    public void sendInvitationNotification(String inviteeEmail, String familyName, String inviterName) {
        try {
            logger.debug("Sending invitation notification to {} for family {} from {}", inviteeEmail, familyName, inviterName);
            
            // Get user by email who should receive the invitation notification
            // INVITATIONS ARE ALWAYS SENT - they are system notifications, not user preferences
            String userSql = "SELECT u.id, u.fcm_token FROM app_user u " +
                           "WHERE u.email = ? " +
                           "AND u.fcm_token IS NOT NULL";
            
            List<Map<String, Object>> recipients = jdbcTemplate.queryForList(userSql, inviteeEmail);
            logger.debug("Found {} invitation notification recipients for email {} (ALWAYS SEND - system notification)", recipients.size(), inviteeEmail);
            
            if (recipients.isEmpty()) {
                logger.debug("No recipients found for invitation notification to {} - user may not exist or have FCM token", inviteeEmail);
                return;
            }
            
            String title = "Family Invitation";
            String body = String.format("%s invited you to join %s", inviterName, familyName);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "INVITATION");
            data.put("familyName", familyName);
            data.put("inviterName", inviterName);
            data.put("inviteeEmail", inviteeEmail);
            
            // Send notification to the invitee
            sendToMultipleDevices(recipients, title, body, data);
            
        } catch (Exception e) {
            logger.error("Error sending invitation notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get family members who should receive new member notifications (using matrix)
     */
    private List<Map<String, Object>> getNewMemberNotificationRecipients(Long familyId) {
        String sql = """
            SELECT DISTINCT u.id as user_id, u.fcm_token, u.username
            FROM user_family_membership ufm
            JOIN app_user u ON ufm.user_id = u.id
            JOIN user_notification_matrix unm ON u.id = unm.user_id
            WHERE ufm.family_id = ?
            AND unm.family_id = 0 AND unm.member_id = 0
            AND unm.device_permission_granted = TRUE
            AND unm.push_enabled = TRUE
            AND unm.new_member_push = TRUE
            AND u.fcm_token IS NOT NULL
        """;
        
        List<Map<String, Object>> recipients = jdbcTemplate.queryForList(sql, familyId);
        logger.debug("Found {} new member notification recipients for family {} (using matrix)", recipients.size(), familyId);
        
        return recipients;
    }
    
    /**
     * Get family members who should receive notifications (excluding sender)
     * Uses the new unified notification matrix for fast lookup
     */
    private List<Map<String, Object>> getNotificationRecipients(Long familyId, Long messageId) {
        String sql = """
            SELECT DISTINCT u.id as user_id, u.fcm_token, u.username
            FROM user_family_membership ufm
            JOIN app_user u ON ufm.user_id = u.id
            JOIN user_notification_matrix unm ON u.id = unm.user_id
            WHERE ufm.family_id = ?
            AND u.id != (SELECT sender_id FROM message WHERE id = ?)
            AND u.fcm_token IS NOT NULL
            AND unm.push_enabled = TRUE
            AND unm.device_permission_granted = TRUE
            AND (
                -- Check global settings (family_id=0) or family-specific override
                (unm.family_id = 0 AND unm.member_id = 0 AND unm.family_messages_push = TRUE)
                OR 
                (unm.family_id = ? AND unm.member_id = 0 AND unm.family_messages_push = TRUE)
            )
            AND u.id NOT IN (
                -- Exclude users who have muted the sender
                SELECT umms.user_id 
                FROM user_member_message_settings umms 
                WHERE umms.member_user_id = (SELECT sender_id FROM message WHERE id = ?)
                AND umms.receive_messages = false
            )
        """;
        
        // Debug: Log the sender ID for this message
        String senderSql = "SELECT sender_id FROM message WHERE id = ?";
        try {
            Long senderId = jdbcTemplate.queryForObject(senderSql, Long.class, messageId);
            logger.debug("Message {} sender_id: {}, family_id: {}", messageId, senderId, familyId);
        } catch (Exception e) {
            logger.error("Could not find sender for message {}: {}", messageId, e.getMessage());
        }
        
        List<Map<String, Object>> recipients = jdbcTemplate.queryForList(sql, familyId, messageId, familyId, messageId);
        logger.debug("Found {} notification recipients for message {} in family {} (using matrix)", recipients.size(), messageId, familyId);
        for (Map<String, Object> recipient : recipients) {
            String fcmToken = (String) recipient.get("fcm_token");
            String tokenPrefix = fcmToken != null && fcmToken.length() > 30 ? fcmToken.substring(0, 30) + "..." : fcmToken;
            logger.debug("Recipient: userId={}, username={}, fcmToken={}", recipient.get("user_id"), recipient.get("username"), tokenPrefix);
        }
        
        return recipients;
    }
    
    /**
     * Check if user has push notifications enabled (using matrix table)
     */
    private boolean hasNotificationsEnabled(Long userId) {
        String sql = """
            SELECT COUNT(*) FROM user_notification_matrix 
            WHERE user_id = ? 
            AND family_id = 0 
            AND member_id = 0
            AND device_permission_granted = TRUE 
            AND push_enabled = TRUE
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }
    
    /**
     * Get FCM token for a user
     */
    private String getFcmToken(Long userId) {
        String sql = "SELECT fcm_token FROM app_user WHERE id = ? AND fcm_token IS NOT NULL";
        
        try {
            return jdbcTemplate.queryForObject(sql, String.class, userId);
        } catch (Exception e) {
            logger.debug("No FCM token found for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get sender ID from message
     */
    private Long getSenderId(Long messageId) {
        String sql = "SELECT sender_id FROM message WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, messageId);
    }
    
    /**
     * Get message author
     */
    private Long getMessageAuthor(Long messageId) {
        String sql = "SELECT sender_id FROM message WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, messageId);
        } catch (Exception e) {
            logger.debug("Could not find message author for message {}: {}", messageId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Truncate message content for notification display
     */
    private String truncateMessage(String content) {
        if (content == null) return "New message";
        if (content.length() <= 100) return content;
        return content.substring(0, 97) + "...";
    }
    
    /**
     * Send notification to multiple devices
     */
    private void sendToMultipleDevices(List<Map<String, Object>> recipients, String title, String body, Map<String, String> data) {
        for (Map<String, Object> recipient : recipients) {
            String fcmToken = (String) recipient.get("fcm_token");
            if (fcmToken != null) {
                sendToDevice(fcmToken, title, body, data);
            }
        }
    }
    
    /**
     * Send notification to a single device
     */
    private void sendToDevice(String fcmToken, String title, String body, Map<String, String> data) {
        try {
            // Check if Firebase is available
            if (FirebaseApp.getApps().isEmpty()) {
                logger.warn("Firebase not initialized, skipping push notification");
                return;
            }
            
            // Send data-only message to let the Flutter app decide whether to show notification
            // This prevents system-level notifications when app is in foreground
            data.put("title", title);  // Include title in data for app to use
            data.put("body", body);    // Include body in data for app to use
            
            // Build the message with NO notification payload - data only
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    // Only set content-available for iOS to wake the app
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setContentAvailable(true)  // Wake the app but don't show notification
                                    .build())
                            .build())
                    .build();
            
            // Send the message
            String response = FirebaseMessaging.getInstance().send(message);
            logger.debug("Successfully sent push notification: {}", response);
            
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                logger.warn("FCM token is invalid, removing from database: {}", fcmToken);
                String cleanupSql = "UPDATE app_user SET fcm_token = NULL WHERE fcm_token = ?";
                jdbcTemplate.update(cleanupSql, fcmToken);
            } else {
                logger.error("Error sending push notification: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error sending push notification: {}", e.getMessage(), e);
        }
    }
} 