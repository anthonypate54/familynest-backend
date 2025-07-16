package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import com.familynest.model.User;
import com.familynest.model.UserDMNotificationSettings;
import com.familynest.model.UserGlobalNotificationSettings;
import com.familynest.model.UserInvitationNotificationSettings;
import com.familynest.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
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
     * Get all notification preferences for a user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getAllNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        logger.debug("Received request to get all notification preferences for user ID: {}", userId);
        try {
            // Validate token and user
            Long tokenUserId = validateUser(authHeader, request);
            if (tokenUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            // Only allow users to view their own preferences
            if (!userId.equals(tokenUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to view preferences for this user"));
            }

            // Use single optimized query to get all notification settings
            String sql = """
                SELECT 
                    -- DM notification settings
                    dm.receive_dm_notifications,
                    dm.email_dm_notifications,
                    dm.push_dm_notifications,
                    dm.last_updated as dm_last_updated,
                    
                    -- Global notification settings
                    g.email_notifications_enabled,
                    g.push_notifications_enabled,
                    g.quiet_hours_enabled,
                    g.quiet_hours_start,
                    g.quiet_hours_end,
                    g.weekend_notifications,
                    g.last_updated as global_last_updated,
                    
                    -- Invitation notification settings
                    i.receive_invitation_notifications,
                    i.email_invitation_notifications,
                    i.push_invitation_notifications,
                    i.notify_on_invitation_accepted,
                    i.notify_on_invitation_declined,
                    i.last_updated as invitation_last_updated
                FROM user_dm_notification_settings dm
                FULL OUTER JOIN user_global_notification_settings g ON dm.user_id = g.user_id
                FULL OUTER JOIN user_invitation_notification_settings i ON dm.user_id = i.user_id
                WHERE dm.user_id = ? OR g.user_id = ? OR i.user_id = ?
                """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId, userId, userId);
            
            // Structure the response
            Map<String, Object> response = new HashMap<>();
            
            // DM settings
            Map<String, Object> dmSettings = new HashMap<>();
            dmSettings.put("receiveDMNotifications", result.get("receive_dm_notifications"));
            dmSettings.put("emailDMNotifications", result.get("email_dm_notifications"));
            dmSettings.put("pushDMNotifications", result.get("push_dm_notifications"));
            dmSettings.put("lastUpdated", result.get("dm_last_updated"));
            response.put("dmNotifications", dmSettings);
            
            // Global settings
            Map<String, Object> globalSettings = new HashMap<>();
            globalSettings.put("emailNotificationsEnabled", result.get("email_notifications_enabled"));
            globalSettings.put("pushNotificationsEnabled", result.get("push_notifications_enabled"));
            globalSettings.put("quietHoursEnabled", result.get("quiet_hours_enabled"));
            globalSettings.put("quietHoursStart", result.get("quiet_hours_start"));
            globalSettings.put("quietHoursEnd", result.get("quiet_hours_end"));
            globalSettings.put("weekendNotifications", result.get("weekend_notifications"));
            globalSettings.put("lastUpdated", result.get("global_last_updated"));
            response.put("globalNotifications", globalSettings);
            
            // Invitation settings
            Map<String, Object> invitationSettings = new HashMap<>();
            invitationSettings.put("receiveInvitationNotifications", result.get("receive_invitation_notifications"));
            invitationSettings.put("emailInvitationNotifications", result.get("email_invitation_notifications"));
            invitationSettings.put("pushInvitationNotifications", result.get("push_invitation_notifications"));
            invitationSettings.put("notifyOnInvitationAccepted", result.get("notify_on_invitation_accepted"));
            invitationSettings.put("notifyOnInvitationDeclined", result.get("notify_on_invitation_declined"));
            invitationSettings.put("lastUpdated", result.get("invitation_last_updated"));
            response.put("invitationNotifications", invitationSettings);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting notification preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Update DM notification preferences
     */
    @PostMapping("/{userId}/dm")
    public ResponseEntity<Map<String, Object>> updateDMNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> preferences,
            HttpServletRequest request) {
        
        logger.debug("Received request to update DM notification preferences for user ID: {}", userId);
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

            // Extract preferences
            Boolean receiveDM = (Boolean) preferences.get("receiveDMNotifications");
            Boolean emailDM = (Boolean) preferences.get("emailDMNotifications");
            Boolean pushDM = (Boolean) preferences.get("pushDMNotifications");

            // Update DM notification settings
            String updateSql = """
                UPDATE user_dm_notification_settings 
                SET receive_dm_notifications = ?, email_dm_notifications = ?, push_dm_notifications = ?, last_updated = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(updateSql, receiveDM, emailDM, pushDM, userId);
            
            if (rowsUpdated == 0) {
                // Create new settings if they don't exist
                String insertSql = """
                    INSERT INTO user_dm_notification_settings (user_id, receive_dm_notifications, email_dm_notifications, push_dm_notifications)
                    VALUES (?, ?, ?, ?)
                    """;
                jdbcTemplate.update(insertSql, userId, receiveDM, emailDM, pushDM);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "DM notification preferences updated successfully");
            response.put("userId", userId);
            response.put("receiveDMNotifications", receiveDM);
            response.put("emailDMNotifications", emailDM);
            response.put("pushDMNotifications", pushDM);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating DM notification preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update DM notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Update global notification preferences
     */
    @PostMapping("/{userId}/global")
    public ResponseEntity<Map<String, Object>> updateGlobalNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> preferences,
            HttpServletRequest request) {
        
        logger.debug("Received request to update global notification preferences for user ID: {}", userId);
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

            // Extract preferences
            Boolean emailEnabled = (Boolean) preferences.get("emailNotificationsEnabled");
            Boolean pushEnabled = (Boolean) preferences.get("pushNotificationsEnabled");
            Boolean quietHoursEnabled = (Boolean) preferences.get("quietHoursEnabled");
            String quietHoursStart = (String) preferences.get("quietHoursStart");
            String quietHoursEnd = (String) preferences.get("quietHoursEnd");
            Boolean weekendNotifications = (Boolean) preferences.get("weekendNotifications");

            // Update global notification settings
            String updateSql = """
                UPDATE user_global_notification_settings 
                SET email_notifications_enabled = ?, push_notifications_enabled = ?, quiet_hours_enabled = ?, 
                    quiet_hours_start = ?, quiet_hours_end = ?, weekend_notifications = ?, last_updated = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(updateSql, emailEnabled, pushEnabled, quietHoursEnabled,
                    quietHoursStart != null ? LocalTime.parse(quietHoursStart) : null,
                    quietHoursEnd != null ? LocalTime.parse(quietHoursEnd) : null,
                    weekendNotifications, userId);
            
            if (rowsUpdated == 0) {
                // Create new settings if they don't exist
                String insertSql = """
                    INSERT INTO user_global_notification_settings (user_id, email_notifications_enabled, push_notifications_enabled, 
                        quiet_hours_enabled, quiet_hours_start, quiet_hours_end, weekend_notifications)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
                jdbcTemplate.update(insertSql, userId, emailEnabled, pushEnabled, quietHoursEnabled,
                        quietHoursStart != null ? LocalTime.parse(quietHoursStart) : LocalTime.of(22, 0),
                        quietHoursEnd != null ? LocalTime.parse(quietHoursEnd) : LocalTime.of(8, 0),
                        weekendNotifications);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Global notification preferences updated successfully");
            response.put("userId", userId);
            response.put("emailNotificationsEnabled", emailEnabled);
            response.put("pushNotificationsEnabled", pushEnabled);
            response.put("quietHoursEnabled", quietHoursEnabled);
            response.put("quietHoursStart", quietHoursStart);
            response.put("quietHoursEnd", quietHoursEnd);
            response.put("weekendNotifications", weekendNotifications);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating global notification preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update global notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Update invitation notification preferences
     */
    @PostMapping("/{userId}/invitations")
    public ResponseEntity<Map<String, Object>> updateInvitationNotificationPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> preferences,
            HttpServletRequest request) {
        
        logger.debug("Received request to update invitation notification preferences for user ID: {}", userId);
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

            // Extract preferences
            Boolean receiveInvitations = (Boolean) preferences.get("receiveInvitationNotifications");
            Boolean emailInvitations = (Boolean) preferences.get("emailInvitationNotifications");
            Boolean pushInvitations = (Boolean) preferences.get("pushInvitationNotifications");
            Boolean notifyOnAccepted = (Boolean) preferences.get("notifyOnInvitationAccepted");
            Boolean notifyOnDeclined = (Boolean) preferences.get("notifyOnInvitationDeclined");

            // Update invitation notification settings
            String updateSql = """
                UPDATE user_invitation_notification_settings 
                SET receive_invitation_notifications = ?, email_invitation_notifications = ?, push_invitation_notifications = ?, 
                    notify_on_invitation_accepted = ?, notify_on_invitation_declined = ?, last_updated = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(updateSql, receiveInvitations, emailInvitations, pushInvitations,
                    notifyOnAccepted, notifyOnDeclined, userId);
            
            if (rowsUpdated == 0) {
                // Create new settings if they don't exist
                String insertSql = """
                    INSERT INTO user_invitation_notification_settings (user_id, receive_invitation_notifications, email_invitation_notifications, 
                        push_invitation_notifications, notify_on_invitation_accepted, notify_on_invitation_declined)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
                jdbcTemplate.update(insertSql, userId, receiveInvitations, emailInvitations, pushInvitations,
                        notifyOnAccepted, notifyOnDeclined);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Invitation notification preferences updated successfully");
            response.put("userId", userId);
            response.put("receiveInvitationNotifications", receiveInvitations);
            response.put("emailInvitationNotifications", emailInvitations);
            response.put("pushInvitationNotifications", pushInvitations);
            response.put("notifyOnInvitationAccepted", notifyOnAccepted);
            response.put("notifyOnInvitationDeclined", notifyOnDeclined);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating invitation notification preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update invitation notification preferences: " + e.getMessage()));
        }
    }

    /**
     * Enable ALL notification preferences for a user (onboarding use case)
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

            // Enable all DM notification settings
            String updateDmSql = """
                INSERT INTO user_dm_notification_settings (user_id, receive_dm_notifications, email_dm_notifications, push_dm_notifications)
                VALUES (?, TRUE, TRUE, TRUE)
                ON CONFLICT (user_id) DO UPDATE SET
                    receive_dm_notifications = TRUE,
                    email_dm_notifications = TRUE,
                    push_dm_notifications = TRUE,
                    last_updated = CURRENT_TIMESTAMP
                """;
            jdbcTemplate.update(updateDmSql, userId);

            // Enable all global notification settings
            String updateGlobalSql = """
                INSERT INTO user_global_notification_settings (user_id, email_notifications_enabled, push_notifications_enabled, weekend_notifications)
                VALUES (?, TRUE, TRUE, TRUE)
                ON CONFLICT (user_id) DO UPDATE SET
                    email_notifications_enabled = TRUE,
                    push_notifications_enabled = TRUE,
                    weekend_notifications = TRUE,
                    last_updated = CURRENT_TIMESTAMP
                """;
            jdbcTemplate.update(updateGlobalSql, userId);

            // Enable all invitation notification settings
            String updateInvitationSql = """
                INSERT INTO user_invitation_notification_settings (user_id, receive_invitation_notifications, email_invitation_notifications, push_invitation_notifications, notify_on_invitation_accepted)
                VALUES (?, TRUE, TRUE, TRUE, TRUE)
                ON CONFLICT (user_id) DO UPDATE SET
                    receive_invitation_notifications = TRUE,
                    email_invitation_notifications = TRUE,
                    push_invitation_notifications = TRUE,
                    notify_on_invitation_accepted = TRUE,
                    last_updated = CURRENT_TIMESTAMP
                """;
            jdbcTemplate.update(updateInvitationSql, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All notification preferences enabled successfully");
            response.put("userId", userId);
            response.put("enabled", Map.of(
                "dmNotifications", true,
                "globalNotifications", true,
                "invitationNotifications", true
            ));
            
            logger.debug("Successfully enabled all notification preferences for user {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error enabling all notification preferences for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to enable notification preferences: " + e.getMessage()));
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