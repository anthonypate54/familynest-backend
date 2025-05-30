package com.familynest.controller;

import com.familynest.model.MessageReaction;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import com.familynest.auth.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class ReactionController {

    private static final Logger logger = LoggerFactory.getLogger(ReactionController.class);

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthUtil authUtil;

    @Transactional
    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<Map<String, Object>> addReaction(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> reactionData,
            HttpServletRequest request) {
        logger.debug("Adding reaction to message ID: {}", messageId);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            String reactionType = reactionData.get("reactionType");
            if (reactionType == null || reactionType.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reaction type is required"));
            }
            
            MessageReaction reaction = engagementService.addReaction(messageId, userId, reactionType);
            
            // If reaction is null, it was removed (toggle behavior)
            if (reaction == null) {
                return ResponseEntity.ok(Map.of("message", "Reaction removed successfully"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", reaction.getId());
            response.put("messageId", reaction.getMessageId());
            response.put("userId", reaction.getUserId());
            response.put("reactionType", reaction.getReactionType());
            response.put("createdAt", reaction.getCreatedAt());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error adding reaction: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error adding reaction: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add reaction: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/reactions")
    public ResponseEntity<Map<String, Object>> getReactions(@PathVariable Long messageId) {
        logger.debug("Getting reactions for message ID: {}", messageId);
        
        try {
            List<MessageReaction> reactions = engagementService.getMessageReactions(messageId);
            Map<String, Long> reactionCounts = engagementService.getMessageReactionCounts(messageId);
            
            // Get user details for each reaction
            List<Map<String, Object>> reactionDetails = new ArrayList<>();
            for (MessageReaction reaction : reactions) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", reaction.getId());
                detail.put("userId", reaction.getUserId());
                detail.put("reactionType", reaction.getReactionType());
                detail.put("createdAt", reaction.getCreatedAt());
                
                // Add basic user info
                User user = userRepository.findById(reaction.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                reactionDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("reactions", reactionDetails);
            response.put("counts", reactionCounts);
            response.put("total", reactions.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting reactions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get reactions: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{messageId}/reactions/{reactionType}")
    public ResponseEntity<Map<String, Object>> removeReaction(
            @PathVariable Long messageId,
            @PathVariable String reactionType,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Removing reaction type: {} from message ID: {}", reactionType, messageId);
        
        try {
            // Get the user ID either from test attributes or from JWT
            Long userId;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                // Use the userId attribute set by the TestAuthFilter
                userId = (Long) userIdAttr;
                logger.debug("Using test userId: {}", userId);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                userId = Long.parseLong(claims.get("userId").toString());
            }
            
            engagementService.removeReaction(messageId, userId, reactionType);
            
            return ResponseEntity.ok(Map.of("message", "Reaction removed successfully"));
        } catch (Exception e) {
            logger.error("Error removing reaction: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove reaction: " + e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/{messageId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                       .body(Map.of("error", "User not authenticated"));
            }

            // Check if like already exists
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, messageId, userId);

            if (!existingReaction.isEmpty()) {
                // Remove like
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE'", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET like_count = like_count - 1 WHERE id = ?", messageId);
                return ResponseEntity.ok(Map.of("action", "removed", "type", "like"));
            } else {
                // Add like
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type) VALUES (?, ?, 'LIKE')", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET like_count = like_count + 1 WHERE id = ?", messageId);
                return ResponseEntity.ok(Map.of("action", "added", "type", "like"));
            }
        } catch (Exception e) {
            logger.error("Error toggling like: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle like: " + e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/{messageId}/love")
    public ResponseEntity<Map<String, Object>> toggleLove(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                       .body(Map.of("error", "User not authenticated"));
            }

            // Check if love already exists
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, messageId, userId);

            if (!existingReaction.isEmpty()) {
                // Remove love
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE'", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET love_count = love_count - 1 WHERE id = ?", messageId);
                return ResponseEntity.ok(Map.of("action", "removed", "type", "love"));
            } else {
                // Add love
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type) VALUES (?, ?, 'LOVE')", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET love_count = love_count + 1 WHERE id = ?", messageId);
                return ResponseEntity.ok(Map.of("action", "added", "type", "love"));
            }
        } catch (Exception e) {
            logger.error("Error toggling love: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle love: " + e.getMessage()));
        }
    }
} 