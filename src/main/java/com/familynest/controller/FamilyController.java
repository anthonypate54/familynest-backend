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
            
            // Deactivate any existing active memberships for this user
            Optional<UserFamilyMembership> activeMembership = userFamilyMembershipRepository.findByUserIdAndIsActiveTrue(userId);
            if (activeMembership.isPresent()) {
                UserFamilyMembership existingActive = activeMembership.get();
                existingActive.setActive(false);
                userFamilyMembershipRepository.save(existingActive);
                logger.debug("Deactivated previous active membership for user ID: {} in family ID: {}", 
                             userId, existingActive.getFamilyId());
            }
            
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
            
            // Get ALL families the user belongs to (not just the first one)
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
} 