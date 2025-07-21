package com.familynest.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
     * Get family members who should receive notifications (excluding sender)
     */
    private List<Map<String, Object>> getNotificationRecipients(Long familyId, Long messageId) {
        String sql = """
            SELECT DISTINCT u.id as user_id, u.fcm_token, u.username
            FROM user_family_membership ufm
            JOIN app_user u ON ufm.user_id = u.id
            JOIN user_notification_settings uns ON u.id = uns.user_id
            LEFT JOIN user_family_message_settings ufms ON (u.id = ufms.user_id AND ufms.family_id = ?)
            WHERE ufm.family_id = ?
            AND uns.device_permission_granted = TRUE
            AND uns.push_notifications_enabled = TRUE
            AND u.id != (SELECT sender_id FROM message WHERE id = ?)
            AND (ufms.receive_messages IS NULL OR ufms.receive_messages = TRUE)
            AND u.fcm_token IS NOT NULL
        """;
        
        return jdbcTemplate.queryForList(sql, familyId, familyId, messageId);
    }
    
    /**
     * Check if user has push notifications enabled
     */
    private boolean hasNotificationsEnabled(Long userId) {
        String sql = """
            SELECT COUNT(*) FROM user_notification_settings 
            WHERE user_id = ? 
            AND device_permission_granted = TRUE 
            AND push_notifications_enabled = TRUE
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
            
            // Build the notification
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();
            
            // Build the message
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putAllData(data)
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build())
                                    .setSound("default")
                                    .setBadge(1)  // Add badge count for iOS
                                    .setContentAvailable(false)  // Ensure it's not a silent notification
                                    .build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setSound("default")
                                    .setChannelId("familynest_channel")
                                    .build())
                            .build())
                    .build();
            
            // Send the message
            String response = FirebaseMessaging.getInstance().send(message);
            logger.debug("Successfully sent push notification: {}", response);
            
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                logger.warn("FCM token is invalid, should remove from database: {}", fcmToken);
                // TODO: Remove invalid token from database
            } else {
                logger.error("Error sending push notification: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error sending push notification: {}", e.getMessage(), e);
        }
    }
} 