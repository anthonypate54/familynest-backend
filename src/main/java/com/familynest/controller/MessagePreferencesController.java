package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import com.familynest.model.User;
import com.familynest.model.UserFamilyMessageSettings;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/message-preferences")
public class MessagePreferencesController {

    private static final Logger logger = LoggerFactory.getLogger(MessagePreferencesController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserFamilyMessageSettingsRepository messageSettingsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Get message preferences for a user
     */
    @GetMapping("/{userId}")
    @Cacheable(value = "messagePreferences", key = "#userId", unless = "#result.status != 200")
    public ResponseEntity<List<Map<String, Object>>> getMessagePreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        logger.debug("Received request to get message preferences for user ID: {}", userId);
        long startTime = System.currentTimeMillis();
        try {
            // Validate token and user
            Long tokenUserId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter or AuthFilter bypass
                tokenUserId = (Long) userIdAttr;
                logger.debug("Using userId from request attribute: {}", tokenUserId);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                tokenUserId = authUtil.extractUserId(token);
                logger.debug("Extracted userId from token: {}", tokenUserId);
            } else {
                logger.debug("No authentication information available");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(List.of(Map.of("error", "Authentication required")));
            }
            
            // Only allow users to view their own preferences
            if (!userId.equals(tokenUserId)) {
                logger.debug("Unauthorized: Token user ID {} does not match path user ID {}", tokenUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(List.of(Map.of("error", "Not authorized to view preferences for this user")));
            }
            
            // Verify user exists - use simple count query instead of fetching full user object
            String userExistsQuery = "SELECT COUNT(*) FROM app_user WHERE id = ?";
            Integer userCount = jdbcTemplate.queryForObject(userExistsQuery, Integer.class, userId);
            if (userCount == null || userCount == 0) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }

            // Use a single optimized SQL query instead of multiple repository calls
            String sql = 
                "SELECT " +
                "  ufm.family_id AS \"familyId\", " +
                "  f.name AS \"familyName\", " +
                "  ufm.role AS \"role\", " +
                "  ufm.is_active AS \"isActive\", " +
                "  COALESCE(ufms.receive_messages, true) AS \"receiveMessages\", " +
                "  ufms.last_updated AS \"lastUpdated\" " +
                "FROM user_family_membership ufm " +
                "JOIN family f ON ufm.family_id = f.id " +
                "LEFT JOIN user_family_message_settings ufms ON " +
                "  ufm.user_id = ufms.user_id AND " +
                "  ufm.family_id = ufms.family_id " +
                "WHERE ufm.user_id = ?";
                
            logger.debug("Executing optimized SQL query for message preferences");
            
            // Execute the query
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, userId);
            
            // Make sure all fields are standardized and camelCase
            for (Map<String, Object> row : result) {
                // Convert boolean type if needed (PostgreSQL returns 't'/'f')
                Object receiveMessages = row.get("receiveMessages");
                if (receiveMessages instanceof String) {
                    row.put("receiveMessages", "t".equalsIgnoreCase((String)receiveMessages));
                }
                
                // Standardize camelCase where needed
                if (row.get("familyId") == null && row.get("familyid") != null) {
                    row.put("familyId", row.get("familyid"));
                }
                if (row.get("familyName") == null && row.get("familyname") != null) {
                    row.put("familyName", row.get("familyname"));
                }
                if (row.get("isActive") == null && row.get("isactive") != null) {
                    row.put("isActive", row.get("isactive"));
                }
                if (row.get("lastUpdated") == null && row.get("lastupdated") != null) {
                    row.put("lastUpdated", row.get("lastupdated"));
                }
            }
            
            logger.debug("Found {} message preferences for user {}", result.size(), userId);
            long endTime = System.currentTimeMillis();
            logger.debug("Message preferences query completed in {} ms", (endTime - startTime));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting message preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", "Failed to get message preferences: " + e.getMessage())));
        }
    }

    /**
     * Update message preferences for a user and family
     */
    @PostMapping("/{userId}/update")
    public ResponseEntity<Map<String, Object>> updateMessagePreferences(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> preferences,
            HttpServletRequest request) {
        
        logger.debug("Received request to update message preferences for user ID: {}", userId);
        try {
            // Validate token and user
            Long tokenUserId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter or AuthFilter bypass
                tokenUserId = (Long) userIdAttr;
                logger.debug("Using userId from request attribute: {}", tokenUserId);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                tokenUserId = authUtil.extractUserId(token);
                logger.debug("Extracted userId from token: {}", tokenUserId);
            } else {
                logger.debug("No authentication information available");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }
            
            // Only allow users to update their own preferences
            if (!userId.equals(tokenUserId)) {
                logger.debug("Unauthorized: Token user ID {} does not match path user ID {}", tokenUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to update preferences for this user"));
            }
            
            // Verify user exists
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User not found"));
            }
            
            // Extract parameters
            Long familyId = Long.valueOf(preferences.get("familyId").toString());
            Boolean receiveMessages = Boolean.valueOf(preferences.get("receiveMessages").toString());
            
            // Verify family exists and user is a member - use simple query
            String membershipQuery = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer membershipCount = jdbcTemplate.queryForObject(membershipQuery, Integer.class, userId, familyId);
            boolean isMember = membershipCount != null && membershipCount > 0;
                    
            if (!isMember) {
                logger.debug("User {} is not a member of family {}", userId, familyId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User is not a member of this family"));
            }
            
            // Update or create settings
            Optional<UserFamilyMessageSettings> settingsOpt = 
                    messageSettingsRepository.findByUserIdAndFamilyId(userId, familyId);
                    
            UserFamilyMessageSettings settings;
            
            if (settingsOpt.isPresent()) {
                // Update existing settings
                settings = settingsOpt.get();
                settings.setReceiveMessages(receiveMessages);
                settings.setLastUpdated(LocalDateTime.now());
            } else {
                // Create new settings
                settings = new UserFamilyMessageSettings(userId, familyId, receiveMessages);
            }
            
            // Save settings to legacy table
            messageSettingsRepository.save(settings);
            
            // CRITICAL: Also update the unified notification matrix table
            // This ensures push notifications respect family-specific settings
            if (!receiveMessages) {
                // User wants to mute this family - create/update override row
                String matrixSql = """
                    INSERT INTO user_notification_matrix (
                        user_id, family_id, member_id,
                        family_messages_push, reactions_push, comments_push,
                        family_messages_websocket, dm_messages_websocket, invitations_websocket,
                        reactions_websocket, comments_websocket, new_member_websocket,
                        dm_messages_push, invitations_push, new_member_push,
                        push_enabled, device_permission_granted
                    )
                    SELECT ?, ?, 0,
                        FALSE, FALSE, FALSE,  -- Mute push notifications for this family
                        TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,  -- Keep websocket for UI updates
                        TRUE, TRUE, TRUE,  -- DMs/invitations/new members not affected by family muting
                        unm_global.push_enabled, unm_global.device_permission_granted
                    FROM user_notification_matrix unm_global
                    WHERE unm_global.user_id = ? AND unm_global.family_id = 0 AND unm_global.member_id = 0
                    ON CONFLICT (user_id, family_id, member_id) DO UPDATE SET
                        family_messages_push = FALSE,
                        reactions_push = FALSE,
                        comments_push = FALSE,
                        updated_at = CURRENT_TIMESTAMP
                    """;
                jdbcTemplate.update(matrixSql, userId, familyId, userId);
                logger.debug("Created/updated matrix override to mute family {} for user {}", familyId, userId);
            } else {
                // User wants to receive messages - remove family-specific override (fall back to global)
                String deleteSql = """
                    DELETE FROM user_notification_matrix
                    WHERE user_id = ? AND family_id = ? AND member_id = 0
                    """;
                int deletedRows = jdbcTemplate.update(deleteSql, userId, familyId);
                logger.debug("Removed {} family-specific override rows for family {} user {}", deletedRows, familyId, userId);
            }
            
            // Get updated family name for response - use simple query
            String familyNameQuery = "SELECT name FROM family WHERE id = ?";
            String familyName;
            try {
                familyName = jdbcTemplate.queryForObject(familyNameQuery, String.class, familyId);
                if (familyName == null) familyName = "Family #" + familyId;
            } catch (Exception e) {
                familyName = "Family #" + familyId;
            }
            
            // Return updated settings
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("familyId", familyId);
            response.put("familyName", familyName);
            response.put("receiveMessages", settings.getReceiveMessages());
            response.put("lastUpdated", settings.getLastUpdated().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating message preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update message preferences: " + e.getMessage()));
        }
    }

    /**
     * Create message settings when a user joins a family
     */
    @PostMapping("/create-for-membership")
    public ResponseEntity<Map<String, Object>> createForMembership(
            @RequestBody Map<String, Object> membershipData) {
        
        logger.debug("Received request to create message settings for new membership");
        try {
            // Extract parameters
            Long userId = Long.valueOf(membershipData.get("userId").toString());
            Long familyId = Long.valueOf(membershipData.get("familyId").toString());
            
            // Check if settings already exist
            Optional<UserFamilyMessageSettings> existingSettings = 
                    messageSettingsRepository.findByUserIdAndFamilyId(userId, familyId);
                    
            if (existingSettings.isPresent()) {
                logger.debug("Message settings already exist for user {} and family {}", userId, familyId);
                return ResponseEntity.ok(Map.of(
                    "message", "Settings already exist",
                    "settings", existingSettings.get()
                ));
            }
            
            // Create new settings with default (receive = true)
            UserFamilyMessageSettings settings = new UserFamilyMessageSettings(userId, familyId, true);
            messageSettingsRepository.save(settings);
            
            logger.debug("Created message settings for user {} and family {}", userId, familyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Settings created successfully",
                "userId", userId,
                "familyId", familyId,
                "receiveMessages", true
            ));
        } catch (Exception e) {
            logger.error("Error creating message settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create message settings: " + e.getMessage()));
        }
    }
} 