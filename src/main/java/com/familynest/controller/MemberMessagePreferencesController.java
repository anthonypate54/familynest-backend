package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;
import com.familynest.model.User;
import com.familynest.model.UserMemberMessageSettings;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.UserMemberMessageSettingsRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/member-message-preferences")
public class MemberMessagePreferencesController {

    private static final Logger logger = LoggerFactory.getLogger(MemberMessagePreferencesController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserMemberMessageSettingsRepository memberMessageSettingsRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Get member-level message preferences for a user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getMemberMessagePreferences(@PathVariable Long userId) {
        logger.debug("Received request to get member message preferences for user ID: {}", userId);
        try {
            // Use a more efficient JOIN query with subqueries for ownership information
            String sql = 
                "SELECT " +
                "  umms.user_id as \"userId\", " +
                "  umms.family_id as \"familyId\", " +
                "  umms.member_user_id as \"memberUserId\", " +
                "  umms.receive_messages as \"receiveMessages\", " +
                "  umms.last_updated as \"lastUpdated\", " +
                "  u.first_name as \"memberFirstName\", " +
                "  u.last_name as \"memberLastName\", " +
                "  u.username as \"memberUsername\", " +
                "  f.name as \"memberOfFamilyName\", " +
                "  (SELECT COUNT(*) > 0 FROM family owned WHERE owned.created_by = umms.member_user_id) as \"isOwner\", " +
                "  (SELECT owned.name FROM family owned WHERE owned.created_by = umms.member_user_id LIMIT 1) as \"ownedFamilyName\" " +
                "FROM user_member_message_settings umms " +
                "JOIN app_user u ON umms.member_user_id = u.id " +
                "JOIN family f ON umms.family_id = f.id " +
                "WHERE umms.user_id = ?";
                
            logger.debug("CUSTOM SQL QUERY EXECUTING: {}", sql);
                
            // Use queryForList but then standardize the field names to ensure consistent camelCase
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            
            // Create a new list with standardized camelCase keys
            List<Map<String, Object>> standardizedResults = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Map<String, Object> standardizedRow = new HashMap<>();
                
                // Copy values with standardized camelCase keys
                standardizedRow.put("userId", row.get("userId") != null ? row.get("userId") : row.get("userid"));
                standardizedRow.put("familyId", row.get("familyId") != null ? row.get("familyId") : row.get("familyid"));
                standardizedRow.put("memberUserId", row.get("memberUserId") != null ? row.get("memberUserId") : row.get("memberuserid"));
                standardizedRow.put("receiveMessages", row.get("receiveMessages") != null ? row.get("receiveMessages") : row.get("receivemessages"));
                standardizedRow.put("lastUpdated", row.get("lastUpdated") != null ? row.get("lastUpdated") : row.get("lastupdated"));
                standardizedRow.put("memberFirstName", row.get("memberFirstName") != null ? row.get("memberFirstName") : row.get("memberfirstname"));
                standardizedRow.put("memberLastName", row.get("memberLastName") != null ? row.get("memberLastName") : row.get("memberlastname"));
                standardizedRow.put("memberUsername", row.get("memberUsername") != null ? row.get("memberUsername") : row.get("memberusername"));
                
                // Handle family name fields (there was a change in field name between versions)
                if (row.containsKey("memberOfFamilyName") || row.containsKey("memberoffamilyname")) {
                    standardizedRow.put("memberOfFamilyName", row.get("memberOfFamilyName") != null ? 
                                      row.get("memberOfFamilyName") : row.get("memberoffamilyname"));
                } else if (row.containsKey("familyName") || row.containsKey("familyname")) {
                    // For backward compatibility with old response format
                    standardizedRow.put("memberOfFamilyName", row.get("familyName") != null ? 
                                      row.get("familyName") : row.get("familyname"));
                }
                
                // Always include isOwner field (set to false if not in original data)
                Boolean isOwner = false;
                if (row.containsKey("isOwner")) {
                    isOwner = (Boolean) row.get("isOwner");
                } else if (row.containsKey("isowner")) {
                    isOwner = (Boolean) row.get("isowner");
                }
                standardizedRow.put("isOwner", isOwner);
                
                // Only include ownedFamilyName if available and user is an owner
                if (isOwner) {
                    if (row.containsKey("ownedFamilyName") && row.get("ownedFamilyName") != null) {
                        standardizedRow.put("ownedFamilyName", row.get("ownedFamilyName"));
                    } else if (row.containsKey("ownedfamilyname") && row.get("ownedfamilyname") != null) {
                        standardizedRow.put("ownedFamilyName", row.get("ownedfamilyname"));
                    }
                }
                
                standardizedResults.add(standardizedRow);
            }
            
            logger.debug("STANDARDIZED RESULTS: {}", standardizedResults);
            
            return ResponseEntity.ok(standardizedResults);
        } catch (Exception e) {
            logger.error("Error getting member message preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", "Failed to get member message preferences: " + e.getMessage())));
        }
    }

    /**
     * Update member-level message preferences for a user
     */
    @PostMapping("/{userId}/update")
    public ResponseEntity<Map<String, Object>> updateMemberMessagePreferences(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> preferences) {
        
        logger.debug("Received request to update member message preferences for user ID: {}", userId);
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
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Extract parameters
            Long familyId = Long.valueOf(preferences.get("familyId").toString());
            Long memberUserId = Long.valueOf(preferences.get("memberUserId").toString());
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
            
            // Verify member exists in the family
            boolean isMemberInFamily = userFamilyMembershipRepository.findByUserIdAndFamilyId(memberUserId, familyId).isPresent();
            if (!isMemberInFamily) {
                logger.debug("Member {} is not in family {}", memberUserId, familyId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Member is not in this family"));
            }
            
            // Update or create settings
            Optional<UserMemberMessageSettings> settingsOpt = 
                    memberMessageSettingsRepository.findByUserIdAndFamilyIdAndMemberUserId(userId, familyId, memberUserId);
                    
            UserMemberMessageSettings settings;
            
            if (settingsOpt.isPresent()) {
                // Update existing settings
                settings = settingsOpt.get();
                settings.setReceiveMessages(receiveMessages);
                settings.setLastUpdated(LocalDateTime.now());
            } else {
                // Create new settings
                settings = new UserMemberMessageSettings(userId, familyId, memberUserId, receiveMessages);
            }
            
            // Save settings
            memberMessageSettingsRepository.save(settings);
            
            // Get member details for response
            Optional<User> memberOpt = userRepository.findById(memberUserId);
            String memberName = memberOpt.map(m -> m.getFirstName() + " " + m.getLastName())
                               .orElse("Unknown Member");
            String username = memberOpt.map(User::getUsername).orElse("unknown");
            
            // Get family name for response
            String familyName = familyRepository.findById(familyId)
                    .map(f -> f.getName())
                    .orElse("Family #" + familyId);
            
            // Check if the member is an owner of any family
            boolean isOwner = false;
            String ownedFamilyName = null;
            
            // Use a simple query to check if this member is a family owner
            String ownerQuery = "SELECT COUNT(*) > 0 as \"isOwner\", (SELECT name FROM family WHERE created_by = ? LIMIT 1) as \"ownedFamilyName\" FROM family WHERE created_by = ?";
            Map<String, Object> ownerResult = jdbcTemplate.queryForMap(ownerQuery, memberUserId, memberUserId);
            
            if (ownerResult.containsKey("isOwner") && (boolean)ownerResult.get("isOwner")) {
                isOwner = true;
                ownedFamilyName = (String)ownerResult.get("ownedFamilyName");
            }
            
            // Return updated settings with standardized field names
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("familyId", familyId);
            response.put("memberOfFamilyName", familyName);
            response.put("memberUserId", memberUserId);
            response.put("memberFirstName", memberOpt.get().getFirstName());
            response.put("memberLastName", memberOpt.get().getLastName());
            response.put("memberUsername", username);
            response.put("receiveMessages", settings.getReceiveMessages());
            response.put("lastUpdated", settings.getLastUpdated().toString());
            response.put("isOwner", isOwner);  // Always include isOwner field
            
            if (isOwner && ownedFamilyName != null) {
                response.put("ownedFamilyName", ownedFamilyName);
            }
            
            response.put("message", "Member message preference updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating member message preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update member message preferences: " + e.getMessage()));
        }
    }
} 