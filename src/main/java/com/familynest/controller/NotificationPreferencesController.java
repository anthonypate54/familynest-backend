package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import com.familynest.model.User;
import com.familynest.model.UserNotificationSettings;
import com.familynest.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferencesController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationPreferencesController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Get notification preferences for a user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        logger.debug("Received request to get notification preferences for user ID: {}", userId);
        
        try {
            Long tokenUserId = validateUser(authHeader, request);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            if (!userId.equals(tokenUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to view preferences for this user"));
            }

            // Get user notification settings from unified matrix table (global row)
            String sql = """
                SELECT device_permission_granted, push_enabled as push_notifications_enabled
                FROM user_notification_matrix 
                WHERE user_id = ? AND family_id = 0 AND member_id = 0
                """;
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);
            
            // For email preferences, we still need to check the old table since matrix doesn't have email fields
            String emailSql = "SELECT email_notifications_enabled FROM user_notification_settings WHERE user_id = ?";
            Boolean emailEnabled = jdbcTemplate.queryForObject(emailSql, Boolean.class, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("devicePermissionGranted", result.get("device_permission_granted"));
            response.put("pushNotificationsEnabled", result.get("push_notifications_enabled"));
            response.put("emailNotificationsEnabled", emailEnabled);
            
            logger.debug("Successfully retrieved notification preferences for user {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving notification preferences for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Update notification preferences for a user
     */
    @PostMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateNotificationPreferences(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> preferences,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        logger.debug("Received request to update notification preferences for user ID: {}", userId);
        
        try {
            Long tokenUserId = validateUser(authHeader, request);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            if (!userId.equals(tokenUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to update preferences for this user"));
            }

            Boolean pushEnabled = (Boolean) preferences.get("pushNotificationsEnabled");
            Boolean emailEnabled = (Boolean) preferences.get("emailNotificationsEnabled");
            
            if (pushEnabled == null || emailEnabled == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Both pushNotificationsEnabled and emailNotificationsEnabled are required"));
            }

            // Update the unified notification matrix table (global settings row)
            String sql = """
                UPDATE user_notification_matrix 
                SET push_enabled = ?, 
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? 
                AND family_id = 0 
                AND member_id = 0
                """;
            
            int rowsUpdated = jdbcTemplate.update(sql, pushEnabled, userId);
            
            // If no global row exists, create one with default settings
            if (rowsUpdated == 0) {
                String insertSql = """
                    INSERT INTO user_notification_matrix (
                        user_id, family_id, member_id, push_enabled, device_permission_granted,
                        family_messages_push, dm_messages_push, invitations_push, 
                        reactions_push, comments_push, new_member_push,
                        family_messages_websocket, dm_messages_websocket, invitations_websocket,
                        reactions_websocket, comments_websocket, new_member_websocket
                    ) VALUES (?, 0, 0, ?, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE)
                    """;
                jdbcTemplate.update(insertSql, userId, pushEnabled);
                logger.debug("Created new matrix row for user {}", userId);
            }
            
            // Also update the legacy table for backward compatibility (temporary)
            String legacySql = """
                INSERT INTO user_notification_settings (user_id, push_notifications_enabled, email_notifications_enabled)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    push_notifications_enabled = EXCLUDED.push_notifications_enabled,
                    email_notifications_enabled = EXCLUDED.email_notifications_enabled,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            jdbcTemplate.update(legacySql, userId, pushEnabled, emailEnabled);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Notification preferences updated successfully");
            response.put("userId", userId);
            response.put("pushNotificationsEnabled", pushEnabled);
            response.put("emailNotificationsEnabled", emailEnabled);
            
            logger.debug("Successfully updated notification preferences for user {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating notification preferences for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Enable all notification preferences for a user (simplified to just enable both push and email)
     * Also sets device_permission_granted = TRUE since this is called when user accepts Firebase permissions
     */
    @PostMapping("/{userId}/enable-all")
    public ResponseEntity<Map<String, Object>> enableAllNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        logger.debug("Received request to enable ALL notification preferences for user ID: {}", userId);
        try {
            Long tokenUserId = validateUser(authHeader, request);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            if (!userId.equals(tokenUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to update preferences for this user"));
            }

            // Enable all notifications AND set device permission granted (since user accepted Firebase dialog)
            String sql = """
                INSERT INTO user_notification_settings (user_id, device_permission_granted, push_notifications_enabled, email_notifications_enabled)
                VALUES (?, TRUE, TRUE, TRUE)
                ON CONFLICT (user_id) DO UPDATE SET
                    device_permission_granted = TRUE,
                    push_notifications_enabled = TRUE,
                    email_notifications_enabled = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            jdbcTemplate.update(sql, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All notification preferences enabled successfully");
            response.put("userId", userId);
            response.put("devicePermissionGranted", true);
            response.put("pushNotificationsEnabled", true);
            response.put("emailNotificationsEnabled", true);
            
            logger.debug("Successfully enabled all notification preferences for user {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error enabling all notification preferences for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to enable notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Sync device permission status without overriding user preferences
     * Only sets device_permission_granted = TRUE, preserves existing push/email preferences
     */
    @PostMapping("/{userId}/sync-device-permission")
    public ResponseEntity<Map<String, Object>> syncDevicePermissionStatus(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        logger.debug("Received request to sync device permission status for user ID: {}", userId);
        try {
            Long tokenUserId = validateUser(authHeader, request);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            if (!userId.equals(tokenUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to update preferences for this user"));
            }

            // Only update device_permission_granted, preserve existing preferences
            String sql = """
                INSERT INTO user_notification_settings (user_id, device_permission_granted, push_notifications_enabled, email_notifications_enabled)
                VALUES (?, TRUE, TRUE, TRUE)
                ON CONFLICT (user_id) DO UPDATE SET
                    device_permission_granted = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            jdbcTemplate.update(sql, userId);

            // Also update the new unified matrix table - only touch device_permission_granted
            String matrixSql = """
                UPDATE user_notification_matrix 
                SET device_permission_granted = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND family_id = 0 AND member_id = 0
                """;
            
            int matrixRowsUpdated = jdbcTemplate.update(matrixSql, userId);
            
            // If no matrix row exists, create one with push_enabled = TRUE (new user setup)
            if (matrixRowsUpdated == 0) {
                String insertMatrixSql = """
                    INSERT INTO user_notification_matrix (
                        user_id, family_id, member_id, push_enabled, device_permission_granted,
                        family_messages_push, dm_messages_push, invitations_push, 
                        reactions_push, comments_push, new_member_push,
                        family_messages_websocket, dm_messages_websocket, invitations_websocket,
                        reactions_websocket, comments_websocket, new_member_websocket
                    ) VALUES (?, 0, 0, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE)
                    """;
                jdbcTemplate.update(insertMatrixSql, userId);
                logger.debug("Created new matrix row for user {} with device permission granted", userId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Device permission status synced successfully");
            response.put("userId", userId);
            response.put("devicePermissionGranted", true);
            
            logger.debug("Successfully synced device permission status for user {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error syncing device permission status for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sync device permission status: " + e.getMessage()));
        }
    }

    /**
     * Helper method to validate user authorization
     */
    private Long validateUser(String authHeader, HttpServletRequest request) {
        try {
            // Check request attribute first (for test scenarios)
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                return (Long) userIdAttr;
            }

            // Check authorization header
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.replace("Bearer ", "");
                return authUtil.extractUserId(token);
            }

            return null;
        } catch (Exception e) {
            logger.error("Error validating user: {}", e.getMessage(), e);
            return null;
        }
    }
} 