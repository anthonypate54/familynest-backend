package com.familynest.controller;

import com.familynest.model.Family;
import com.familynest.model.User;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import com.familynest.repository.InvitationRepository;
import com.familynest.model.Invitation;
import com.familynest.auth.AuthUtil;
import com.familynest.auth.JwtUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private static final Logger logger = LoggerFactory.getLogger(FamilyController.class);

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Create a new family
     * POST /api/families
     */
    @PostMapping("")
    public ResponseEntity<Map<String, Object>> createFamily(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> familyData) {
        logger.debug("Received request to create family");
        try {
            // Extract user ID from token (consistent with other methods)
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }
            logger.debug("Creating family for user ID: {}", userId);

            // Check if user exists using lightweight query
            String userCheckSql = "SELECT id FROM app_user WHERE id = ?";
            List<Long> userIds = jdbcTemplate.queryForList(userCheckSql, Long.class, userId);
            if (userIds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }

            String familyName = familyData.get("name");
            if (familyName == null || familyName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Family name is required"));
            }

            // Create the family using direct SQL
            String insertFamilySql = "INSERT INTO family (name, created_by) VALUES (?, ?)";
            jdbcTemplate.update(insertFamilySql, familyName.trim(), userId);
            
            // Get the created family ID
            String getFamilyIdSql = "SELECT id FROM family WHERE name = ? AND created_by = ? ORDER BY id DESC LIMIT 1";
            Long familyId = jdbcTemplate.queryForObject(getFamilyIdSql, Long.class, familyName.trim(), userId);
            
            if (familyId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to create family"));
            }
            
            logger.debug("Created family with ID: {}", familyId);

            // Create UserFamilyMembership for the creator using direct SQL
            String insertMembershipSql = "INSERT INTO user_family_membership (user_id, family_id, role, joined_at, is_active) VALUES (?, ?, 'ADMIN', NOW(), true)";
            jdbcTemplate.update(insertMembershipSql, userId, familyId);
            logger.debug("Created UserFamilyMembership for user ID: {} and family ID: {}", userId, familyId);

            // Return the created family details
            Map<String, Object> response = new HashMap<>();
            response.put("id", familyId);
            response.put("name", familyName.trim());
            response.put("createdBy", userId);
            response.put("memberCount", 1);
            response.put("message", "Family created successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error creating family: " + e.getMessage()));
        }
    }

    /**
     * Get family details by ID
     * GET /api/families/{familyId}
     */
    @GetMapping("/{familyId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting family details for ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Check if user is a member of this family using lightweight query
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized to view this family"));
            }

            // Get family details with lightweight query (no password, no unnecessary fields)
            String familySql = "SELECT f.id, f.name, f.created_by, " +
                              "(SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count " +
                              "FROM family f WHERE f.id = ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(familySql, familyId);
            
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Family not found"));
            }
            
            Map<String, Object> familyData = results.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("id", familyData.get("id"));
            response.put("name", familyData.get("name"));
            response.put("createdBy", familyData.get("created_by"));
            response.put("memberCount", familyData.get("member_count"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error getting family: " + e.getMessage()));
        }
    }

    /**
     * Update family details
     * PUT /api/families/{familyId}
     */
    @PutMapping("/{familyId}")
    public ResponseEntity<Map<String, Object>> updateFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> familyData) {
        logger.debug("Received request to update family ID: {}", familyId);
        try {
            // Validate JWT token and get user ID
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
            Long userId = Long.parseLong(claims.get("sub").toString());
            logger.debug("Token validated for user ID: {}", userId);
            
            // Check if family exists and get basic info using lightweight query
            String familyCheckSql = "SELECT f.id, f.name, f.created_by FROM family f WHERE f.id = ?";
            List<Map<String, Object>> familyResults = jdbcTemplate.queryForList(familyCheckSql, familyId);
            
            if (familyResults.isEmpty()) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Family not found"));
            }
            
            Map<String, Object> familyInfo = familyResults.get(0);
            Long createdBy = ((Number) familyInfo.get("created_by")).longValue();
            
            // Verify user is admin of this family
            boolean isAdmin = false;
            
            // Check if user is the creator
            if (createdBy.equals(userId)) {
                isAdmin = true;
            } else {
                // Check if user is an admin member using lightweight query
                String adminCheckSql = "SELECT role FROM user_family_membership WHERE user_id = ? AND family_id = ?";
                List<String> roles = jdbcTemplate.queryForList(adminCheckSql, String.class, userId, familyId);
                isAdmin = !roles.isEmpty() && "ADMIN".equals(roles.get(0));
            }
            
            if (!isAdmin) {
                logger.debug("User {} is not authorized to update family {}", userId, familyId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized to update this family"));
            }
            
            // Update family name if provided
            if (familyData.containsKey("name") && familyData.get("name") != null && !familyData.get("name").trim().isEmpty()) {
                String newName = familyData.get("name").trim();
                String updateSql = "UPDATE family SET name = ? WHERE id = ?";
                jdbcTemplate.update(updateSql, newName, familyId);
                logger.debug("Updated family name to: {}", newName);
            }
            
            // Get updated family details with member count
            String resultSql = "SELECT f.id, f.name, " +
                              "(SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count " +
                              "FROM family f WHERE f.id = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(resultSql, familyId);
            
            if (results.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to retrieve updated family"));
            }
            
            Map<String, Object> updatedFamily = results.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedFamily.get("id"));
            response.put("name", updatedFamily.get("name"));
            response.put("memberCount", updatedFamily.get("member_count"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error updating family: " + e.getMessage()));
        }
    }

    /**
     * Get family members
     * GET /api/families/{familyId}/members
     */
    @GetMapping("/{familyId}/members")
    public ResponseEntity<List<Map<String, Object>>> getFamilyMembers(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to get family members for family ID: {}", familyId);
        try {
            // Extract userId from token and validate authorization
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long tokenUserId = authUtil.extractUserId(token);
            if (tokenUserId == null) {
                logger.debug("Token validation failed or userId could not be extracted");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            
            // Check if user is a member of this family
            Optional<UserFamilyMembership> membership = userFamilyMembershipRepository.findByUserIdAndFamilyId(tokenUserId, familyId);
            if (membership.isEmpty()) {
                logger.debug("User {} is not authorized to view members of family {}", tokenUserId, familyId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            
            // Get family members using optimized query
            String sql = "SELECT " +
                        "    u.id, u.username, u.first_name, u.last_name, u.photo, " +
                        "    f.name as family_name, f.created_by as family_owner_id, " +
                        "    ufm.role as membership_role, " +
                        "    CASE WHEN of.id IS NOT NULL THEN true ELSE false END as is_owner, " +
                        "    of.name as owned_family_name " +
                        "FROM user_family_membership ufm " + 
                        "JOIN app_user u ON u.id = ufm.user_id " +
                        "JOIN family f ON f.id = ufm.family_id " +
                        "LEFT JOIN family of ON of.created_by = u.id " +
                        "WHERE ufm.family_id = ? ORDER BY u.first_name, u.last_name";
                        
            logger.debug("Executing query for family members for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Transform results into the response format
            List<Map<String, Object>> response = results.stream().map(member -> {
                Map<String, Object> memberMap = new HashMap<>();
                memberMap.put("id", member.get("id"));
                memberMap.put("userId", member.get("id")); // Add userId field for consistency
                memberMap.put("username", member.get("username"));
                memberMap.put("firstName", member.get("first_name"));
                memberMap.put("lastName", member.get("last_name"));
                memberMap.put("photo", member.get("photo"));
                memberMap.put("familyId", familyId); // Add the missing familyId field
                memberMap.put("familyName", member.get("family_name"));
                memberMap.put("isOwner", member.get("is_owner"));
                
                // Only include ownedFamilyName if user is an owner
                if ((boolean)member.get("is_owner") && member.get("owned_family_name") != null) {
                    memberMap.put("ownedFamilyName", member.get("owned_family_name"));
                }
                
                return memberMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} family members", response.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving family members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Join a family
     * POST /api/families/{familyId}/join
     */
    @PostMapping("/{familyId}/join")
    public ResponseEntity<Map<String, Object>> joinFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to join family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Check if user is already a member of this family
            Optional<UserFamilyMembership> existingMembership = userFamilyMembershipRepository.findByUserIdAndFamilyId(userId, familyId);
            if (existingMembership.isPresent()) {
                logger.debug("User ID: {} is already in family ID: {}", userId, familyId);
                return ResponseEntity.badRequest().body(Map.of("error", "User is already in that family"));
            }
            
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family == null) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.badRequest().body(Map.of("error", "Family not found"));
            }
            
            logger.debug("Creating UserFamilyMembership for user ID: {} and family ID: {}", userId, familyId);
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(userId);
            membership.setFamilyId(familyId);
            membership.setActive(true);
            membership.setRole("MEMBER");
            
            // Multi-family support: User can belong to multiple families simultaneously
            
            // Save the new membership as active
            userFamilyMembershipRepository.save(membership);
            logger.debug("Created and activated new membership for user ID: {} in family ID: {}", userId, familyId);
            
            // Create message settings (default: receive messages = true)
            logger.debug("Creating message settings for user ID: {} and family ID: {}", userId, familyId);
            try {
                com.familynest.model.UserFamilyMessageSettings settings = 
                    new com.familynest.model.UserFamilyMessageSettings(userId, familyId, true);
                userFamilyMessageSettingsRepository.save(settings);
                logger.debug("Message settings created successfully");
            } catch (Exception e) {
                logger.error("Error creating message settings: {}", e.getMessage());
                // Continue anyway, don't fail the whole operation
            }
            
            logger.debug("User ID: {} joined family ID: {}", userId, familyId);
            return ResponseEntity.ok(Map.of("message", "Successfully joined family"));
        } catch (Exception e) {
            logger.error("Error joining family: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Error joining family: " + e.getMessage()));
        }
    }

    /**
     * Leave a family
     * POST /api/families/{familyId}/leave
     */
    @PostMapping("/{familyId}/leave")
    public ResponseEntity<Map<String, Object>> leaveFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to leave family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Check if user exists
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Check if user is in this family
            Optional<UserFamilyMembership> membership = userFamilyMembershipRepository.findByUserIdAndFamilyId(userId, familyId);
            if (membership.isEmpty()) {
                logger.debug("User ID {} is not in family ID {}", userId, familyId);
                return ResponseEntity.badRequest().body(Map.of("error", "User is not in this family"));
            }
            
            // Set membership as inactive instead of deleting
            UserFamilyMembership userMembership = membership.get();
            userMembership.setActive(false);
            userFamilyMembershipRepository.save(userMembership);
            
            logger.debug("User ID: {} left family ID: {}", userId, familyId);
            return ResponseEntity.ok(Map.of("message", "Successfully left family"));
        } catch (Exception e) {
            logger.error("Error leaving family: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Error leaving family: " + e.getMessage()));
        }
    }

    @PostMapping("/{familyId}/invite") 
    @Transactional
    public ResponseEntity<Map<String, Object>> inviteUser(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> inviteData) {
        logger.debug("Received request to invite user to family ID: {}", familyId);
        
        // This functionality has been moved to InvitationController
        // Please use /api/invitations/{familyId}/invite endpoint instead
        logger.info("Redirecting invite request to InvitationController");
        
        // Forward to the InvitationController endpoint
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .header("Location", "/api/invitations/" + familyId + "/invite")
            .body(Map.of(
                "message", "This endpoint has been moved to /api/invitations/{familyId}/invite",
                "redirectTo", "/api/invitations/" + familyId + "/invite",
                "info", "The method has been renamed from inviteUser to inviteUserToFamily"
            ));
    }

    @GetMapping("/{id}/families")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getUserFamilies(@PathVariable Long id) {
        logger.debug("Getting families for user ID: {}", id);
        try {
            // Check if the user exists using a lightweight check
            if (!userRepository.existsById(id)) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }
            
            // Simplified SQL query to get all family data
            String sql = "SELECT " +
                        "  f.id as family_id, f.name as family_name, f.created_by as owner_id, " +
                        "  (SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count, " +
                        "  ufm.role, ufm.is_active, " +
                        "  CASE WHEN f.created_by = ? THEN true ELSE false END as is_owner " +
                        "FROM family f " +
                        "JOIN user_family_membership ufm ON f.id = ufm.family_id " +
                        "WHERE ufm.user_id = ?";
            
            logger.debug("Executing query for families for user ID: {}", id);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id, id);
            
            // Transform results into the response format  
            List<Map<String, Object>> families = results.stream().map(family -> {
                Map<String, Object> familyInfo = new HashMap<>();
                familyInfo.put("familyId", family.get("family_id"));
                familyInfo.put("familyName", family.get("family_name"));
                familyInfo.put("memberCount", family.get("member_count"));
                familyInfo.put("isOwner", family.get("is_owner"));
                familyInfo.put("role", family.get("role"));
                familyInfo.put("isActive", family.get("is_active"));
                return familyInfo;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} families for user {}", families.size(), id);
            return ResponseEntity.ok(families);
        } catch (Exception e) {
            logger.error("Error getting families for user {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Error getting families: " + e.getMessage())));
        }
    }

 
    /**
     * Check if user owns a family and return family details if they do
     * GET /api/families/owned
     */
    /**
     * Get all family members across all families the user belongs to
     * This is specifically for DM recipient selection
     * GET /api/families/all-members
     */
    @GetMapping("/all-members")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAllFamilyMembers(
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to get all family members for DM recipient selection");
        try {
            // Extract userId from token and validate authorization
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long tokenUserId = authUtil.extractUserId(token);
            if (tokenUserId == null) {
                logger.debug("Token validation failed or userId could not be extracted");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            
            // Get ALL families the user belongs to (only active memberships for DM purposes)
            String sql = "WITH user_families AS (" +
                        "  SELECT ufm.family_id " +
                        "  FROM user_family_membership ufm " +
                        "  WHERE ufm.user_id = ? " +
                        "  AND ufm.is_active = true" +
                        "), " +
                        "all_family_members AS (" +
                        "  SELECT DISTINCT " +
                        "    u.id, u.username, u.first_name, u.last_name, u.photo, ufm2.family_id, " +
                        "    f.name as family_name, f.created_by as family_owner_id, " +
                        "    ufm.role as membership_role, " +
                        "    CASE WHEN of.id IS NOT NULL THEN true ELSE false END as is_owner, " +
                        "    of.name as owned_family_name " +
                        "  FROM user_families uf " +
                        "  JOIN user_family_membership ufm2 ON ufm2.family_id = uf.family_id " + 
                        "  JOIN app_user u ON u.id = ufm2.user_id " +
                        "  JOIN family f ON f.id = uf.family_id " +
                        "  LEFT JOIN user_family_membership ufm ON ufm.user_id = u.id AND ufm.family_id = uf.family_id " +
                        "  LEFT JOIN family of ON of.created_by = u.id " +
                        "  WHERE u.id != ? " + // Exclude the requesting user
                        ") " +
                        "SELECT * FROM all_family_members " +
                        "ORDER BY family_name, first_name, last_name";
                        
            logger.debug("Executing query for all family members for user ID: {}", tokenUserId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, tokenUserId, tokenUserId);
            
            // Transform results into the response format
            List<Map<String, Object>> response = results.stream().map(member -> {
                Map<String, Object> memberMap = new HashMap<>();
                memberMap.put("id", member.get("id"));
                memberMap.put("userId", member.get("id")); // Add userId field for consistency
                memberMap.put("username", member.get("username"));
                memberMap.put("firstName", member.get("first_name"));
                memberMap.put("lastName", member.get("last_name"));
                memberMap.put("photo", member.get("photo"));
                memberMap.put("familyId", member.get("family_id"));
                memberMap.put("familyName", member.get("family_name"));
                memberMap.put("isOwner", member.get("is_owner"));
                
                // Only include ownedFamilyName if user is an owner
                if ((boolean)member.get("is_owner") && member.get("owned_family_name") != null) {
                    memberMap.put("ownedFamilyName", member.get("owned_family_name"));
                }
                
                return memberMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} family members across all families for user {}", response.size(), tokenUserId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving all family members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/owned")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getOwnedFamily(
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Checking if user owns a family");
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Check if user has created a family (owns one)
            String ownedFamilySql = "SELECT f.id, f.name, " +
                                   "(SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count " +
                                   "FROM family f WHERE f.created_by = ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(ownedFamilySql, userId);
            
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User does not own a family"));
            }
            
            Map<String, Object> familyData = results.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("familyId", familyData.get("id"));
            response.put("familyName", familyData.get("name"));
            response.put("memberCount", familyData.get("member_count"));
            response.put("isOwner", true);
            response.put("role", "ADMIN");
            
            logger.debug("User {} owns family: {}", userId, familyData.get("name"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking owned family: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User does not own a family"));
        }
    }

    /**
     * Get complete family data for a user - families, members, and preferences in one call
     * GET /api/families/complete-data
     */
    @GetMapping("/complete-data")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getCompleteFamilyData(
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            logger.debug("Getting complete family data for user {} with backend processing", userId);

            // Query 1: Get all families for the user
            String familiesSql = "SELECT " +
                    "f.id as family_id, f.name as family_name, f.created_by as owner_id, " +
                    "(SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count, " +
                    "ufm.role, ufm.is_active, " +
                    "CASE WHEN f.created_by = ? THEN true ELSE false END as is_owner " +
                    "FROM family f " +
                    "JOIN user_family_membership ufm ON f.id = ufm.family_id " +
                    "WHERE ufm.user_id = ?";

            List<Map<String, Object>> familyResults = jdbcTemplate.queryForList(familiesSql, userId, userId);

            // Query 2: Get all members across all families
            String membersSql = "WITH user_families AS (" +
                    "  SELECT ufm.family_id " +
                    "  FROM user_family_membership ufm " +
                    "  WHERE ufm.user_id = ? " +
                    ") " +
                    "SELECT DISTINCT " +
                    "  u.id, u.username, u.first_name, u.last_name, u.photo, " +
                    "  ufm.family_id, f.name as family_name, " +
                    "  ufm.role as membership_role, ufm.joined_at, ufm.is_new_member, " +
                    "  CASE WHEN of.id IS NOT NULL THEN true ELSE false END as is_owner, " +
                    "  of.name as owned_family_name " +
                    "FROM user_families uf " +
                    "JOIN user_family_membership ufm ON ufm.family_id = uf.family_id " +
                    "JOIN app_user u ON u.id = ufm.user_id " +
                    "JOIN family f ON f.id = uf.family_id " +
                    "LEFT JOIN family of ON of.created_by = u.id " +
                    "ORDER BY f.name, u.first_name, u.last_name";

            List<Map<String, Object>> memberResults = jdbcTemplate.queryForList(membersSql, userId);

            // Query 3: Get family-level message preferences
            String preferencesSql = "SELECT family_id, receive_messages FROM user_family_message_settings WHERE user_id = ?";
            List<Map<String, Object>> familyPreferences = jdbcTemplate.queryForList(preferencesSql, userId);

            // Query 4: Get member-specific message preferences
            String memberPreferencesSql = "SELECT family_id, member_user_id, receive_messages " +
                    "FROM user_member_message_settings WHERE user_id = ?";
            List<Map<String, Object>> memberPreferences = jdbcTemplate.queryForList(memberPreferencesSql, userId);

            // Build lookup maps for preferences (done once in backend)
            Map<Long, Boolean> familyPrefsMap = new HashMap<>();
            for (Map<String, Object> pref : familyPreferences) {
                familyPrefsMap.put((Long) pref.get("family_id"), (Boolean) pref.get("receive_messages"));
            }

            Map<String, Boolean> memberPrefsMap = new HashMap<>();
            for (Map<String, Object> pref : memberPreferences) {
                String key = pref.get("family_id") + "_" + pref.get("member_user_id");
                memberPrefsMap.put(key, (Boolean) pref.get("receive_messages"));
            }

            // Group members by family (done once in backend)
            Map<Long, List<Map<String, Object>>> membersByFamily = new HashMap<>();
            for (Map<String, Object> member : memberResults) {
                Long familyId = (Long) member.get("family_id");
                membersByFamily.computeIfAbsent(familyId, k -> new ArrayList<>()).add(member);
            }

            // Build final families with embedded members and preferences
            List<Map<String, Object>> families = new ArrayList<>();
            for (Map<String, Object> familyData : familyResults) {
                Long familyId = (Long) familyData.get("family_id");
                
                // Build family object
                Map<String, Object> family = new HashMap<>();
                family.put("familyId", familyId);
                family.put("familyName", familyData.get("family_name"));
                family.put("memberCount", familyData.get("member_count"));
                family.put("isOwner", familyData.get("is_owner"));
                family.put("role", familyData.get("role"));
                family.put("isActive", familyData.get("is_active"));
                family.put("isMuted", !familyPrefsMap.getOrDefault(familyId, true));
                family.put("receiveMessages", familyPrefsMap.getOrDefault(familyId, true));

                // Add members with their preferences applied
                List<Map<String, Object>> familyMembers = new ArrayList<>();
                List<Map<String, Object>> rawMembers = membersByFamily.getOrDefault(familyId, new ArrayList<>());
                
                for (Map<String, Object> memberData : rawMembers) {
                    Map<String, Object> member = new HashMap<>();
                    member.put("id", memberData.get("id"));
                    member.put("userId", memberData.get("id"));
                    member.put("username", memberData.get("username"));
                    member.put("firstName", memberData.get("first_name"));
                    member.put("lastName", memberData.get("last_name"));
                    member.put("photo", memberData.get("photo"));
                    member.put("familyId", familyId);
                    member.put("familyName", memberData.get("family_name"));
                    member.put("role", memberData.get("membership_role"));
                    member.put("joinedAt", memberData.get("joined_at"));
                    member.put("isOwner", memberData.get("is_owner"));
                    
                    // Apply member preferences
                    String prefKey = familyId + "_" + memberData.get("id");
                    boolean receiveMessages = memberPrefsMap.getOrDefault(prefKey, true);
                    member.put("isMuted", !receiveMessages);
                    member.put("receiveMessages", receiveMessages);
                    member.put("isNewMember", memberData.get("is_new_member"));
                    
                    if ((Boolean) memberData.get("is_owner") && memberData.get("owned_family_name") != null) {
                        member.put("ownedFamilyName", memberData.get("owned_family_name"));
                    }
                    
                    familyMembers.add(member);
                }
                
                family.put("members", familyMembers);
                families.add(family);
            }

            // Build simple response - no raw data, just the final structure
            Map<String, Object> response = new HashMap<>();
            response.put("families", families);
            response.put("userId", userId);

            logger.debug("Returning processed family data for user {} - {} families with embedded members", 
                    userId, families.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting complete family data: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error getting family data: " + e.getMessage()));
        }
    }

    /**
     * Send welcome message to family and mark member as no longer new
     * POST /api/families/{familyId}/welcome/{memberId}
     */
    @PostMapping("/{familyId}/welcome/{memberId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendWelcomeMessage(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> messageData) {
        logger.debug("Sending welcome message to member {} in family {}", memberId, familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Verify user is admin of this family
            boolean isAdmin = false;
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family != null && family.getCreatedBy() != null && family.getCreatedBy().getId().equals(userId)) {
                isAdmin = true;
            } else {
                Optional<UserFamilyMembership> membership = userFamilyMembershipRepository.findByUserIdAndFamilyId(userId, familyId);
                isAdmin = membership.isPresent() && "ADMIN".equals(membership.get().getRole());
            }

            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized to send welcome messages"));
            }

            // Mark member as no longer new
            String updateMemberSql = "UPDATE user_family_membership SET is_new_member = false WHERE user_id = ? AND family_id = ?";
            int updated = jdbcTemplate.update(updateMemberSql, memberId, familyId);
            
            if (updated == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Member not found in family"));
            }

            return ResponseEntity.ok(Map.of("message", "Welcome message sent and member status updated"));
        } catch (Exception e) {
            logger.error("Error sending welcome message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error sending welcome message: " + e.getMessage()));
        }
    }

    /**
     * Get upcoming birthdays in the next 7 days for a family
     * GET /api/families/{familyId}/birthdays
     */
    @GetMapping("/{familyId}/birthdays")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getUpcomingBirthdays(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting upcoming birthdays for family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Check if user is a member of this family
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Complex SQL query to find birthdays in the next 7 days, handling year rollover
            String sql = "WITH upcoming_dates AS (" +
                        "  SELECT " +
                        "    CURRENT_DATE + generate_series(0, 6) AS target_date " +
                        "), " +
                        "birthday_matches AS (" +
                        "  SELECT DISTINCT " +
                        "    u.id, u.first_name, u.last_name, u.birth_date, u.photo, " +
                        "    ud.target_date, " +
                        "    CASE " +
                        "      WHEN ud.target_date = CURRENT_DATE THEN 0 " +
                        "      ELSE (ud.target_date - CURRENT_DATE) " +
                        "    END AS days_until " +
                        "  FROM user_family_membership ufm " +
                        "  JOIN app_user u ON u.id = ufm.user_id " +
                        "  CROSS JOIN upcoming_dates ud " +
                        "  WHERE ufm.family_id = ? " +
                        "    AND u.birth_date IS NOT NULL " +
                        "    AND EXTRACT(MONTH FROM u.birth_date) = EXTRACT(MONTH FROM ud.target_date) " +
                        "    AND EXTRACT(DAY FROM u.birth_date) = EXTRACT(DAY FROM ud.target_date) " +
                        ") " +
                        "SELECT * FROM birthday_matches " +
                        "ORDER BY days_until ASC, first_name ASC";

            logger.debug("Executing birthday query for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Transform results into response format
            List<Map<String, Object>> birthdays = results.stream().map(birthday -> {
                Map<String, Object> birthdayMap = new HashMap<>();
                birthdayMap.put("id", birthday.get("id"));
                birthdayMap.put("firstName", birthday.get("first_name"));
                birthdayMap.put("lastName", birthday.get("last_name"));
                birthdayMap.put("birthDate", birthday.get("birth_date").toString());
                birthdayMap.put("photo", birthday.get("photo"));
                birthdayMap.put("targetDate", birthday.get("target_date").toString());
                birthdayMap.put("daysUntil", birthday.get("days_until"));
                
                // Add a readable date description
                int daysUntil = ((Number) birthday.get("days_until")).intValue();
                String description;
                if (daysUntil == 0) {
                    description = "Today";
                } else if (daysUntil == 1) {
                    description = "Tomorrow";
                } else {
                    description = "In " + daysUntil + " days";
                }
                birthdayMap.put("description", description);
                
                return birthdayMap;
            }).collect(Collectors.toList());
            
            logger.debug("Found {} upcoming birthdays for family {}", birthdays.size(), familyId);
            return ResponseEntity.ok(birthdays);
            
        } catch (Exception e) {
            logger.error("Error getting upcoming birthdays: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get weekly message activity for a family (past 7 days)
     * GET /api/families/{familyId}/weekly-activity
     */
    @GetMapping("/{familyId}/weekly-activity")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getWeeklyActivity(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting weekly activity for family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Check if user is a member of this family
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Get message counts for the past 7 days
            String sql = "WITH date_series AS (" +
                        "  SELECT " +
                        "    (CURRENT_DATE - generate_series(6, 0, -1)) AS message_date " +
                        "), " +
                        "family_members AS (" +
                        "  SELECT user_id FROM user_family_membership WHERE family_id = ? " +
                        "), " +
                        "daily_counts AS (" +
                        "  SELECT " +
                        "    ds.message_date, " +
                        "    COALESCE(COUNT(m.id), 0) as message_count " +
                        "  FROM date_series ds " +
                        "  LEFT JOIN message m ON DATE(m.timestamp) = ds.message_date " +
                        "    AND m.sender_id IN (SELECT user_id FROM family_members) " +
                        "  GROUP BY ds.message_date " +
                        "  ORDER BY ds.message_date " +
                        ") " +
                        "SELECT " +
                        "  message_date, " +
                        "  message_count, " +
                        "  EXTRACT(DOW FROM message_date) as day_of_week " +
                        "FROM daily_counts " +
                        "ORDER BY message_date";

            logger.debug("Executing weekly activity query for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Get total message count for this family
            String totalSql = "SELECT COUNT(*) as total_messages " +
                             "FROM message m " +
                             "WHERE m.sender_id IN (SELECT user_id FROM user_family_membership WHERE family_id = ?)";
            
            Integer totalMessages = jdbcTemplate.queryForObject(totalSql, Integer.class, familyId);
            
            // Transform results into response format
            List<Map<String, Object>> dailyActivity = results.stream().map(day -> {
                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", day.get("message_date").toString());
                dayMap.put("messageCount", day.get("message_count"));
                dayMap.put("dayOfWeek", day.get("day_of_week"));
                
                // Add day label (M, T, W, T, F, S, S)
                int dow = ((Number) day.get("day_of_week")).intValue();
                String[] dayLabels = {"S", "M", "T", "W", "T", "F", "S"}; // 0=Sunday
                dayMap.put("dayLabel", dayLabels[dow]);
                
                return dayMap;
            }).collect(Collectors.toList());
            
            // Calculate weekly total
            int weeklyTotal = dailyActivity.stream()
                .mapToInt(day -> ((Number) day.get("messageCount")).intValue())
                .sum();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("dailyActivity", dailyActivity);
            response.put("weeklyTotal", weeklyTotal);
            response.put("totalMessages", totalMessages != null ? totalMessages : 0);
            response.put("familyId", familyId);
            
            logger.debug("Found weekly activity for family {}: {} messages this week, {} total", 
                        familyId, weeklyTotal, totalMessages);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting weekly activity: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get monthly message activity for a family (past 30 days)
     * GET /api/families/{familyId}/monthly-activity
     */
    @GetMapping("/{familyId}/monthly-activity")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getMonthlyActivity(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting monthly activity for family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Check if user is a member of this family
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Get message counts for the past 30 days
            String sql = "WITH date_series AS (" +
                        "  SELECT " +
                        "    (CURRENT_DATE - generate_series(29, 0, -1)) AS message_date " +
                        "), " +
                        "family_members AS (" +
                        "  SELECT user_id FROM user_family_membership WHERE family_id = ? " +
                        "), " +
                        "daily_counts AS (" +
                        "  SELECT " +
                        "    ds.message_date, " +
                        "    COALESCE(COUNT(m.id), 0) as message_count " +
                        "  FROM date_series ds " +
                        "  LEFT JOIN message m ON DATE(m.timestamp) = ds.message_date " +
                        "    AND m.sender_id IN (SELECT user_id FROM family_members) " +
                        "  GROUP BY ds.message_date " +
                        "  ORDER BY ds.message_date " +
                        ") " +
                        "SELECT " +
                        "  message_date, " +
                        "  message_count, " +
                        "  EXTRACT(DAY FROM message_date) as day_of_month " +
                        "FROM daily_counts " +
                        "ORDER BY message_date";

            logger.debug("Executing monthly activity query for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Get total message count for this family
            String totalSql = "SELECT COUNT(*) as total_messages " +
                             "FROM message m " +
                             "WHERE m.sender_id IN (SELECT user_id FROM user_family_membership WHERE family_id = ?)";
            
            Integer totalMessages = jdbcTemplate.queryForObject(totalSql, Integer.class, familyId);
            
            // Transform results into response format
            List<Map<String, Object>> dailyActivity = results.stream().map(day -> {
                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", day.get("message_date").toString());
                dayMap.put("messageCount", day.get("message_count"));
                dayMap.put("dayOfMonth", day.get("day_of_month"));
                
                // Add day label (just the day number for monthly view)
                int dayOfMonth = ((Number) day.get("day_of_month")).intValue();
                dayMap.put("dayLabel", String.valueOf(dayOfMonth));
                
                return dayMap;
            }).collect(Collectors.toList());
            
            // Calculate monthly total
            int monthlyTotal = dailyActivity.stream()
                .mapToInt(day -> ((Number) day.get("messageCount")).intValue())
                .sum();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("dailyActivity", dailyActivity);
            response.put("monthlyTotal", monthlyTotal);
            response.put("totalMessages", totalMessages != null ? totalMessages : 0);
            response.put("familyId", familyId);
            
            logger.debug("Found monthly activity for family {}: {} messages this month, {} total", 
                        familyId, monthlyTotal, totalMessages);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting monthly activity: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get yearly message activity for a family (past 12 months)
     * GET /api/families/{familyId}/yearly-activity
     */
    @GetMapping("/{familyId}/yearly-activity")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getYearlyActivity(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting yearly activity for family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Check if user is a member of this family
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Get message counts for the past 12 months
            String sql = "WITH month_series AS (" +
                        "  SELECT " +
                        "    DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month' * generate_series(11, 0, -1)) AS month_start " +
                        "), " +
                        "family_members AS (" +
                        "  SELECT user_id FROM user_family_membership WHERE family_id = ? " +
                        "), " +
                        "monthly_counts AS (" +
                        "  SELECT " +
                        "    ms.month_start, " +
                        "    COALESCE(COUNT(m.id), 0) as message_count " +
                        "  FROM month_series ms " +
                        "  LEFT JOIN message m ON DATE_TRUNC('month', m.timestamp) = ms.month_start " +
                        "    AND m.sender_id IN (SELECT user_id FROM family_members) " +
                        "  GROUP BY ms.month_start " +
                        "  ORDER BY ms.month_start " +
                        ") " +
                        "SELECT " +
                        "  month_start, " +
                        "  message_count, " +
                        "  EXTRACT(MONTH FROM month_start) as month_number " +
                        "FROM monthly_counts " +
                        "ORDER BY month_start";

            logger.debug("Executing yearly activity query for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Get total message count for this family
            String totalSql = "SELECT COUNT(*) as total_messages " +
                             "FROM message m " +
                             "WHERE m.sender_id IN (SELECT user_id FROM user_family_membership WHERE family_id = ?)";
            
            Integer totalMessages = jdbcTemplate.queryForObject(totalSql, Integer.class, familyId);
            
            // Transform results into response format
            List<Map<String, Object>> monthlyActivity = results.stream().map(month -> {
                Map<String, Object> monthMap = new HashMap<>();
                monthMap.put("date", month.get("month_start").toString());
                monthMap.put("messageCount", month.get("message_count"));
                monthMap.put("monthNumber", month.get("month_number"));
                
                // Add month label (abbreviated month names)
                int monthNumber = ((Number) month.get("month_number")).intValue();
                String[] monthLabels = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                monthMap.put("monthLabel", monthLabels[monthNumber]);
                
                return monthMap;
            }).collect(Collectors.toList());
            
            // Calculate yearly total
            int yearlyTotal = monthlyActivity.stream()
                .mapToInt(month -> ((Number) month.get("messageCount")).intValue())
                .sum();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("monthlyActivity", monthlyActivity);
            response.put("yearlyTotal", yearlyTotal);
            response.put("totalMessages", totalMessages != null ? totalMessages : 0);
            response.put("familyId", familyId);
            
            logger.debug("Found yearly activity for family {}: {} messages this year, {} total", 
                        familyId, yearlyTotal, totalMessages);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting yearly activity: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get multi-year message activity for a family (past 5 years)
     * GET /api/families/{familyId}/multi-year-activity
     */
    @GetMapping("/{familyId}/multi-year-activity")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getMultiYearActivity(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Getting multi-year activity for family ID: {}", familyId);
        try {
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Check if user is a member of this family
            String membershipSql = "SELECT COUNT(*) FROM user_family_membership WHERE user_id = ? AND family_id = ?";
            Integer memberCount = jdbcTemplate.queryForObject(membershipSql, Integer.class, userId, familyId);
            
            if (memberCount == null || memberCount == 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            // Get message counts for the past 5 years
            String sql = "WITH year_series AS (" +
                        "  SELECT " +
                        "    EXTRACT(YEAR FROM CURRENT_DATE) - generate_series(4, 0, -1) AS year_value " +
                        "), " +
                        "family_members AS (" +
                        "  SELECT user_id FROM user_family_membership WHERE family_id = ? " +
                        "), " +
                        "yearly_counts AS (" +
                        "  SELECT " +
                        "    ys.year_value, " +
                        "    COALESCE(COUNT(m.id), 0) as message_count " +
                        "  FROM year_series ys " +
                        "  LEFT JOIN message m ON EXTRACT(YEAR FROM m.timestamp) = ys.year_value " +
                        "    AND m.sender_id IN (SELECT user_id FROM family_members) " +
                        "  GROUP BY ys.year_value " +
                        "  ORDER BY ys.year_value " +
                        ") " +
                        "SELECT " +
                        "  year_value, " +
                        "  message_count " +
                        "FROM yearly_counts " +
                        "ORDER BY year_value";

            logger.debug("Executing multi-year activity query for family ID: {}", familyId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, familyId);
            
            // Get total message count for this family
            String totalSql = "SELECT COUNT(*) as total_messages " +
                             "FROM message m " +
                             "WHERE m.sender_id IN (SELECT user_id FROM user_family_membership WHERE family_id = ?)";
            
            Integer totalMessages = jdbcTemplate.queryForObject(totalSql, Integer.class, familyId);
            
            // Transform results into response format
            List<Map<String, Object>> yearlyActivity = results.stream().map(year -> {
                Map<String, Object> yearMap = new HashMap<>();
                yearMap.put("date", year.get("year_value").toString());
                yearMap.put("messageCount", year.get("message_count"));
                yearMap.put("year", year.get("year_value"));
                
                // Add year label
                String yearLabel = year.get("year_value").toString();
                yearMap.put("yearLabel", yearLabel);
                
                return yearMap;
            }).collect(Collectors.toList());
            
            // Calculate total for all years
            int allYearsTotal = yearlyActivity.stream()
                .mapToInt(year -> ((Number) year.get("messageCount")).intValue())
                .sum();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("yearlyActivity", yearlyActivity);
            response.put("allYearsTotal", allYearsTotal);
            response.put("totalMessages", totalMessages != null ? totalMessages : 0);
            response.put("familyId", familyId);
            
            logger.debug("Found multi-year activity for family {}: {} messages across years, {} total", 
                        familyId, allYearsTotal, totalMessages);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting multi-year activity: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }
} 