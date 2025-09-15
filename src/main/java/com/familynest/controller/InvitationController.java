package com.familynest.controller;

import com.familynest.model.Family;
import com.familynest.model.User;
import com.familynest.model.UserFamilyMembership;
import com.familynest.model.Invitation;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.InvitationRepository;
import com.familynest.auth.AuthUtil;
import com.familynest.service.WebSocketBroadcastService;
import com.familynest.service.PushNotificationService;
import com.familynest.service.EmailProviderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private static final Logger logger = LoggerFactory.getLogger(InvitationController.class);

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebSocketBroadcastService webSocketBroadcastService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private EmailProviderService emailProviderService;

    /**
     * Get invitations for the current user
     * GET /api/invitations
     */
    @GetMapping("")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getInvitations(@RequestHeader(value = "Authorization", required = true) String authHeader) {
        logger.info("🔍 INVITATIONS: Request received - authHeader present: {}", authHeader != null);
        logger.debug("Received request to get invitations");
        try {
            if (authHeader == null || authHeader.trim().isEmpty()) {
                return ResponseEntity.status(403).body(List.of(Map.of("error", "Missing authorization header")));
            }

            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(List.of(Map.of("error", "Unauthorized access")));
            }

            // Simplified SQL query for debugging
            String sql = "SELECT i.id, i.family_id, i.sender_id, i.status, i.created_at, i.expires_at, i.email, " +
                        "       f.name as family_name, " +
                        "       s.username as sender_username, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                        "       u.email as user_email " +
                        "FROM invitation i " +
                        "LEFT JOIN family f ON i.family_id = f.id " +
                        "LEFT JOIN app_user s ON i.sender_id = s.id " +
                        "LEFT JOIN app_user u ON u.id = ? " +
                        "WHERE i.email = (SELECT email FROM app_user WHERE id = ?)";
            
            logger.debug("Executing query for invitations for user ID: {}", userId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, userId);
            
            // Add debug logging
            logger.info("🔍 DEBUG: Raw results count: {}", results.size());
            String emailCheckSql = "SELECT email FROM app_user WHERE id = ?";
            List<String> userEmails = jdbcTemplate.queryForList(emailCheckSql, String.class, userId);
            logger.info("🔍 DEBUG: User {} email: {}", userId, userEmails.isEmpty() ? "NOT FOUND" : userEmails.get(0));
           
            // Transform results into the response format
            List<Map<String, Object>> response = results.stream().map(inv -> {
                Map<String, Object> invMap = new HashMap<>();
                invMap.put("id", inv.get("id"));
                invMap.put("familyId", inv.get("family_id"));
                invMap.put("inviterId", inv.get("sender_id"));
                invMap.put("status", inv.get("status"));
                invMap.put("createdAt", inv.get("created_at").toString());
                invMap.put("expiresAt", inv.get("expires_at").toString());
                
                // Add additional information
                invMap.put("familyName", inv.get("family_name"));
                invMap.put("senderName", inv.get("sender_first_name") + " " + inv.get("sender_last_name"));
                invMap.put("senderUsername", inv.get("sender_username"));
                
                // Debug info
                invMap.put("invitationEmail", inv.get("email"));
                invMap.put("userEmail", inv.get("user_email"));
                
                return invMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} invitations for user ID: {}", response.size(), userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving invitations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Error retrieving invitations: " + e.getMessage())));
        }
    }

    @PostMapping("/{familyId}/invite") 
    @Transactional
    public ResponseEntity<Map<String, Object>> inviteUserToFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> inviteData) {
        logger.debug("Received request to invite user to family ID: {}", familyId);
        try {
            // Validate the request
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            if (!authUtil.validateToken(token)) {
                logger.debug("Invalid token");
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }
    
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                logger.debug("Could not extract user ID from token");
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }
    
            // Get the user and verify they exist
            User inviter = userRepository.findById(userId).orElse(null);
            if (inviter == null) {
                logger.debug("Inviter not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "Inviter not found"));
            }
    
            // Check if user is authorized for this specific family
            List<Map<String, Object>> userFamilies = getUserFamiliesForInvitation(userId).getBody();
            boolean canInviteToFamily = false;
    
            if (userFamilies != null) {
                for (Map<String, Object> family : userFamilies) {
                    Object familyIdObj = family.get("familyId");
                    Long currentFamilyId = null;
                    
                    if (familyIdObj instanceof Number) {
                        currentFamilyId = ((Number) familyIdObj).longValue();
                    } else if (familyIdObj instanceof String) {
                        try {
                            currentFamilyId = Long.parseLong((String) familyIdObj);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse familyId: {}", familyIdObj);
                            continue;
                        }
                    }
                    
                    if (currentFamilyId != null && currentFamilyId.equals(familyId) && 
                        (Boolean) family.getOrDefault("isOwner", false)) {
                        canInviteToFamily = true;
                        logger.debug("User ID {} is authorized to invite to family ID {}", userId, familyId);
                        break;
                    }
                }
            }
    
            if (!canInviteToFamily) {
                logger.debug("User ID {} is not authorized to invite to family ID {}", userId, familyId);
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to invite to this family"));
            }
    
            String inviteeEmail = inviteData.get("email");
            if (inviteeEmail == null || inviteeEmail.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitee email is required"));
            }

            // HYBRID APPROACH: Check if email exists and provide enhanced feedback
            User recipient = userRepository.findByEmail(inviteeEmail).orElse(null);
            boolean userExists = recipient != null;
            
            // If user doesn't exist, check for similar emails to help with typos
            List<String> suggestedEmails = new ArrayList<>();
            if (!userExists) {
                // Find emails that are similar (basic similarity check)
                String emailUsername = inviteeEmail.contains("@") ? inviteeEmail.split("@")[0] : inviteeEmail;
                String emailDomain = inviteeEmail.contains("@") ? inviteeEmail.split("@")[1] : "";
                
                // Query for similar usernames or emails
                String similarEmailQuery = "SELECT email FROM app_user WHERE " +
                    "LOWER(email) LIKE LOWER(?) OR " +
                    "LOWER(username) LIKE LOWER(?) OR " +
                    "LOWER(email) LIKE LOWER(?) " +
                    "LIMIT 3";
                
                try {
                    List<String> similarResults = jdbcTemplate.queryForList(
                        similarEmailQuery, 
                        String.class, 
                        "%" + emailUsername + "%",
                        "%" + emailUsername + "%", 
                        "%" + emailDomain + "%"
                    );
                    
                    // Filter out exact matches and add to suggestions
                    suggestedEmails = similarResults.stream()
                        .filter(email -> !email.equalsIgnoreCase(inviteeEmail))
                        .distinct()
                        .collect(Collectors.toList());
                        
                    if (!suggestedEmails.isEmpty()) {
                        logger.debug("Found {} similar emails for '{}': {}", 
                            suggestedEmails.size(), inviteeEmail, suggestedEmails);
                    }
                } catch (Exception e) {
                    logger.warn("Error finding similar emails: {}", e.getMessage());
                    // Continue without suggestions
                }
            }
    
            // Validate email format
            if (inviteeEmail == null || inviteeEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email address is required"));
            }
            
            // Basic email validation
            String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
            if (!inviteeEmail.matches(emailRegex)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please enter a valid email address"));
            }
            
            // Normalize email to lowercase for consistent checking
            String normalizedEmail = inviteeEmail.toLowerCase().trim();
            
            // Check if invitee already has any invitation to this family (PENDING or ACCEPTED)
            List<Invitation> pendingInvitations = invitationRepository.findByEmailAndFamilyIdAndStatus(
                normalizedEmail, familyId, "PENDING");
            List<Invitation> acceptedInvitations = invitationRepository.findByEmailAndFamilyIdAndStatus(
                normalizedEmail, familyId, "ACCEPTED");
                
            if (!pendingInvitations.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "An invitation to this family is already pending for this email");
                errorResponse.put("userExists", userExists);
                if (!suggestedEmails.isEmpty()) {
                    errorResponse.put("suggestedEmails", suggestedEmails);
                }
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (!acceptedInvitations.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "This email has already accepted an invitation to this family");
                errorResponse.put("userExists", userExists);
                return ResponseEntity.badRequest().body(errorResponse);
            }
    
            // Create and save the invitation
            Invitation invitation = new Invitation();
            invitation.setFamilyId(familyId);
            invitation.setSenderId(userId);
            invitation.setEmail(normalizedEmail);
            invitation.setStatus("PENDING");
            invitation.setCreatedAt(LocalDateTime.now());
            invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
            
            Invitation savedInvitation = invitationRepository.save(invitation);
    
            logger.debug("Invitation created successfully: ID={}, familyId={}, email={}, userExists={}", 
                savedInvitation.getId(), familyId, inviteeEmail, userExists);

            // Send invitation email
            try {
                Family family = familyRepository.findById(familyId).orElse(null);
                String familyName = family != null ? family.getName() : "Unknown Family";
                String inviterName = inviter.getFirstName() + " " + inviter.getLastName();
                
                emailProviderService.sendFamilyInvitationEmail(inviteeEmail, familyName, inviterName, savedInvitation.getId().toString());
                logger.info("Invitation email sent successfully to: {}", inviteeEmail);
            } catch (Exception e) {
                logger.error("Failed to send invitation email to {}: {}", inviteeEmail, e.getMessage(), e);
                // Don't fail the invitation creation if email fails
            }

            // Broadcast new invitation to recipient via WebSocket (only if user exists)
            if (userExists) {
                try {
                    Map<String, Object> invitationData = new HashMap<>();
                    invitationData.put("id", savedInvitation.getId());
                    invitationData.put("familyId", familyId);
                    invitationData.put("inviterId", userId);
                    invitationData.put("status", savedInvitation.getStatus());
                    invitationData.put("createdAt", savedInvitation.getCreatedAt().toString());
                    invitationData.put("expiresAt", savedInvitation.getExpiresAt().toString());
                    invitationData.put("email", inviteeEmail);
                    invitationData.put("type", "NEW_INVITATION");
                    
                    // Get family and inviter names
                    Family family = familyRepository.findById(familyId).orElse(null);
                    if (family != null) {
                        invitationData.put("familyName", family.getName());
                    }
                    invitationData.put("inviterName", inviter.getFirstName() + " " + inviter.getLastName());
                    
                    // Schedule WebSocket broadcast to happen AFTER transaction commits
                    final Map<String, Object> finalInvitationData = invitationData;
                    final Long finalRecipientId = recipient.getId();
                    
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            webSocketBroadcastService.broadcastInvitation(finalInvitationData, finalRecipientId);
                            logger.debug("Broadcasted new invitation to user: {} (AFTER COMMIT)", finalRecipientId);
                            
                            // Send push notification for the invitation
                            String familyName = (String) finalInvitationData.get("familyName");
                            String inviterName = (String) finalInvitationData.get("inviterName");
                            pushNotificationService.sendInvitationNotification(inviteeEmail, familyName, inviterName);
                            logger.debug("Sent push notification for invitation to user: {} (AFTER COMMIT)", finalRecipientId);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error broadcasting new invitation: {}", e.getMessage(), e);
                    // Don't fail the request if WebSocket broadcast fails
                }
            } else {
                logger.debug("No WebSocket broadcast sent - user does not exist yet for email: {}", inviteeEmail);
            }
    
            // Enhanced response with user existence info and suggestions
            Map<String, Object> response = new HashMap<>();
            response.put("invitationId", savedInvitation.getId());
            response.put("familyId", familyId);
            response.put("email", inviteeEmail);
            response.put("status", savedInvitation.getStatus());
            response.put("expiresAt", savedInvitation.getExpiresAt().toString());
            response.put("userExists", userExists);
            
            if (userExists) {
                response.put("message", "Invitation sent successfully to registered user");
                response.put("recipientName", recipient.getFirstName() + " " + recipient.getLastName());
            } else {
                response.put("message", "Invitation sent to unregistered email - they can join when they sign up");
                if (!suggestedEmails.isEmpty()) {
                    response.put("suggestedEmails", suggestedEmails);
                    response.put("suggestionMessage", "Did you mean one of these registered emails?");
                }
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating invitation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error creating invitation: " + e.getMessage()));
        }
    }

    /**
     * Respond to an invitation (accept or decline)
     * POST /api/invitations/{invitationId}/respond
     */
    @PostMapping("/{invitationId}/respond")
    @Transactional
    public ResponseEntity<Map<String, Object>> respondToInvitation(
            @PathVariable Long invitationId,
            @RequestHeader(value = "Authorization", required = true) String authHeader,
            @RequestBody Map<String, Object> responseData) {
        logger.debug("Received request to respond to invitation ID: {}", invitationId);
        try {
            if (authHeader == null || authHeader.trim().isEmpty()) {
                return ResponseEntity.status(403).body(Map.of("error", "Missing authorization header"));
            }
            
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }

            Boolean accept = (Boolean) responseData.get("accept");
            if (accept == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Accept parameter is required"));
            }

            // Find the invitation
            Optional<Invitation> invitationOpt = invitationRepository.findById(invitationId);
            if (invitationOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation not found"));
            }

            Invitation invitation = invitationOpt.get();
            
            // Verify the invitation is for this user's email
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.getEmail().equals(invitation.getEmail())) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to respond to this invitation"));
            }

            if (accept) {
                // Accept invitation - add user to family
                invitation.setStatus("ACCEPTED");
                
                // Check if user is already a member
                Optional<UserFamilyMembership> existingMembership = 
                    userFamilyMembershipRepository.findByUserIdAndFamilyId(userId, invitation.getFamilyId());
                
                if (existingMembership.isEmpty()) {
                    // MULTI-FAMILY MEMBERSHIP: User can belong to multiple families simultaneously
                    UserFamilyMembership membership = new UserFamilyMembership();
                    membership.setUserId(userId);
                    membership.setFamilyId(invitation.getFamilyId());
                    membership.setRole("MEMBER");
                    membership.setActive(true);  // Keep all family memberships active
                    userFamilyMembershipRepository.save(membership);
                    
                    logger.debug("User {} accepted invitation to family {} (multi-family: added to new family)", 
                                 userId, invitation.getFamilyId());
                    
                    // Send new member notification to existing family members
                    try {
                        String newMemberName = user.getFirstName() + " " + user.getLastName();
                        Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
                        if (family != null) {
                            pushNotificationService.sendNewMemberNotification(
                                invitation.getFamilyId(), 
                                newMemberName, 
                                family.getName()
                            );
                            logger.debug("Sent new member notification for {} joining {}", newMemberName, family.getName());
                        }
                    } catch (Exception e) {
                        logger.error("Error sending new member notification: {}", e.getMessage());
                        // Don't fail the invitation acceptance if notification fails
                    }
                } else {
                    // User is already a member - just ensure membership is active
                    UserFamilyMembership existingMember = existingMembership.get();
                    existingMember.setActive(true);
                    userFamilyMembershipRepository.save(existingMember);
                    
                    logger.debug("User {} reactivated membership in family {}", userId, invitation.getFamilyId());
                }
            } else {
                // Decline invitation
                invitation.setStatus("DECLINED");
                logger.debug("User {} declined invitation to family {}", userId, invitation.getFamilyId());
            }
            
            invitationRepository.save(invitation);

            // Broadcast invitation response to the sender via WebSocket
            try {
                Map<String, Object> invitationResponseData = new HashMap<>();
                invitationResponseData.put("id", invitation.getId());
                invitationResponseData.put("familyId", invitation.getFamilyId());
                invitationResponseData.put("status", invitation.getStatus());
                invitationResponseData.put("email", invitation.getEmail());
                invitationResponseData.put("responderId", userId);
                invitationResponseData.put("type", accept ? "INVITATION_ACCEPTED" : "INVITATION_DECLINED");
                
                // Add responder name
                invitationResponseData.put("responderName", user.getFirstName() + " " + user.getLastName());
                
                // Get family name
                Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
                if (family != null) {
                    invitationResponseData.put("familyName", family.getName());
                }
                
                webSocketBroadcastService.broadcastInvitation(invitationResponseData, invitation.getSenderId());
                logger.debug("Broadcasted invitation response to sender: {}", invitation.getSenderId());
                
            } catch (Exception e) {
                logger.error("Error broadcasting invitation response: {}", e.getMessage(), e);
                // Don't fail the request if WebSocket broadcast fails
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", invitation.getStatus());
            response.put("message", accept ? "Invitation accepted successfully" : "Invitation declined");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error responding to invitation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error responding to invitation: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/user-families")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getUserFamiliesForInvitation(@PathVariable Long id) {
        logger.debug("Getting families for user ID: {} for invitation purposes", id);
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
     * Get sent invitations by the current user, grouped by family
     * GET /api/invitations/sent
     */
    @GetMapping("/sent")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getSentInvitations(@RequestHeader(value = "Authorization", required = true) String authHeader) {
        logger.debug("Getting sent invitations for current user");
        try {
            if (authHeader == null || authHeader.trim().isEmpty()) {
                return ResponseEntity.status(403).body(Map.of("error", "Missing authorization header"));
            }

            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }

            // Get all invitations sent by the current user
            String sql = "SELECT i.id, i.family_id, i.email, i.status, i.created_at, i.expires_at, " +
                        "       f.name as family_name " +
                        "FROM invitation i " +
                        "LEFT JOIN family f ON i.family_id = f.id " +
                        "WHERE i.sender_id = ? " +
                        "ORDER BY i.created_at DESC";
                        
            // First, let's do a direct count to see what we expect
            String countSql = "SELECT COUNT(*) FROM invitation WHERE sender_id = ? AND status = 'PENDING'";
            Integer expectedPendingCount = jdbcTemplate.queryForObject(countSql, Integer.class, userId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            
            
       
            // Transform results into the response format
            List<Map<String, Object>> invitations = results.stream().map(inv -> {
                Map<String, Object> invMap = new HashMap<>();
                invMap.put("id", inv.get("id"));
                invMap.put("familyId", inv.get("family_id"));
                invMap.put("email", inv.get("email"));
                invMap.put("status", inv.get("status"));
                invMap.put("createdAt", inv.get("created_at").toString());
                invMap.put("expiresAt", inv.get("expires_at").toString());
                invMap.put("familyName", inv.get("family_name"));
                return invMap;
            }).collect(Collectors.toList());
            
             
            // Count pending invitations by family for debug
            Map<Object, Long> pendingByFamily = invitations.stream()
                .filter(inv -> "PENDING".equals(inv.get("status")))
                .collect(Collectors.groupingBy(inv -> inv.get("familyId"), Collectors.counting()));
            
             
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("invitations", invitations);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving sent invitations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error retrieving sent invitations: " + e.getMessage()));
        }
    }
}