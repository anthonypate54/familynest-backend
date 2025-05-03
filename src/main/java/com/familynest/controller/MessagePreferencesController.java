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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private JwtUtil jwtUtil;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Get message preferences for a user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getMessagePreferences(@PathVariable Long userId) {
        logger.debug("Received request to get message preferences for user ID: {}", userId);
        try {
            // Verify user exists
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }

            // Get all families the user belongs to
            List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(userId);
            
            // Get all message settings
            List<UserFamilyMessageSettings> settings = messageSettingsRepository.findByUserId(userId);
            
            // Map of family ID to settings
            Map<Long, UserFamilyMessageSettings> settingsMap = settings.stream()
                    .collect(Collectors.toMap(UserFamilyMessageSettings::getFamilyId, s -> s));
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            // Build response with all families and their message settings
            for (UserFamilyMembership membership : memberships) {
                Long familyId = membership.getFamilyId();
                
                // Try to fetch family name, default to "Family #ID" if not found
                String familyName = familyRepository.findById(familyId)
                        .map(f -> f.getName())
                        .orElse("Family #" + familyId);
                
                // Get settings if they exist, otherwise use default (receive = true)
                UserFamilyMessageSettings setting = settingsMap.getOrDefault(
                    familyId, 
                    new UserFamilyMessageSettings(userId, familyId, true)
                );
                
                Map<String, Object> familySettings = new HashMap<>();
                familySettings.put("familyId", familyId);
                familySettings.put("familyName", familyName);
                familySettings.put("receiveMessages", setting.getReceiveMessages());
                familySettings.put("role", membership.getRole());
                familySettings.put("isActive", membership.isActive());
                familySettings.put("lastUpdated", setting.getLastUpdated() != null ? 
                                                  setting.getLastUpdated().toString() : null);
                
                result.add(familySettings);
            }
            
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
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> preferences) {
        
        logger.debug("Received request to update message preferences for user ID: {}", userId);
        try {
            // Validate token and user
            String token = authHeader.replace("Bearer ", "");
            Long tokenUserId = authUtil.extractUserId(token);
            
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
            
            // Verify family exists and user is a member
            List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(userId);
            boolean isMember = memberships.stream()
                    .anyMatch(m -> m.getFamilyId().equals(familyId));
                    
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
            
            // Save settings
            messageSettingsRepository.save(settings);
            
            // Get updated family name for response
            String familyName = familyRepository.findById(familyId)
                    .map(f -> f.getName())
                    .orElse("Family #" + familyId);
            
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