package com.familynest.controller;

import com.familynest.model.MessageReaction;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.service.WebSocketBroadcastService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    @Autowired
    private WebSocketBroadcastService webSocketBroadcastService;

    /*
     * Note on Message vs Comment Reactions:
     * - Both messages and comments use the same message_reaction table
     * - The distinction is in the target table:
     *   - message: Regular messages with no parent_id
     *   - message_comment: Comments in threads with a NOT NULL parent_message_id
     * - IDs are unique across both tables, so message_reaction.message_id can reference either
     * - The appropriate count is updated based on which table the ID exists in
     */

    public ReactionController(JdbcTemplate jdbcTemplate, AuthUtil authUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.authUtil = authUtil;
    }

    @PostMapping("/{messageId}/reactions")
    @Transactional
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
    @Transactional
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

    @PostMapping("/{messageId}/message_like")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleMessageLike(
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
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'MESSAGE'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, messageId, userId);

            String action;
            if (!existingReaction.isEmpty()) {
                // Remove like
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'MESSAGE'", 
                    messageId, userId);
                    
                jdbcTemplate.update("UPDATE message SET like_count = like_count - 1 WHERE id = ?", messageId);
                action = "removed";
            } else {
                // Add like
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type, target_type) VALUES (?, ?, 'LIKE', 'MESSAGE')", 
                    messageId, userId);

                jdbcTemplate.update("UPDATE message SET like_count = like_count + 1 WHERE id = ?", messageId);
                action = "added";
            }

            // Get the complete reaction data using the same query pattern as getMessages
            String reactionDataSql = """
                SELECT 
                    m.id, m.like_count, m.love_count,
                    CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked,
                    CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved
                FROM message m
                LEFT JOIN message_reaction mr ON m.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'MESSAGE'
                LEFT JOIN message_reaction mr2 ON m.id = mr2.message_id AND mr2.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'MESSAGE'
                WHERE m.id = ?
                """;
            
            Map<String, Object> reactionData = jdbcTemplate.queryForMap(reactionDataSql, userId, userId, messageId);

            // Get families where this message is visible
            String familiesSql = "SELECT family_id FROM message_family_link WHERE message_id = ?";
            List<Map<String, Object>> families = jdbcTemplate.queryForList(familiesSql, messageId);

            // Broadcast reaction update to all families
            // Schedule WebSocket broadcast to happen AFTER transaction commits
            final Map<String, Object> finalReactionData = new HashMap<>(reactionData);
            final Long finalMessageId = messageId;
            final String finalAction = action;
            final List<Map<String, Object>> finalFamilies = new ArrayList<>(families);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Map<String, Object> family : finalFamilies) {
                        Long familyId = ((Number) family.get("family_id")).longValue();
                        finalReactionData.put("target_type", "MESSAGE");
                        finalReactionData.put("action", finalAction);
                        finalReactionData.put("reaction_type", "LIKE");
                        webSocketBroadcastService.broadcastReaction(finalReactionData, finalMessageId, familyId);
                        logger.debug("Broadcasted message reaction to family {} (AFTER COMMIT)", familyId);
                    }
                }
            });

            return ResponseEntity.ok(Map.of("action", action, "type", "like", "like_count", reactionData.get("like_count")));
        } catch (Exception e) {
            logger.error("Error toggling message like: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle message like: " + e.getMessage()));
        }
    }

    @PostMapping("/{messageId}/message_love")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleMessageLove(
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
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'MESSAGE'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, messageId, userId);

            String action;
            if (!existingReaction.isEmpty()) {
                // Remove love
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'MESSAGE'", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET love_count = love_count - 1 WHERE id = ?", messageId);
                action = "removed";
            } else {
                // Add love
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type, target_type) VALUES (?, ?, 'LOVE', 'MESSAGE')", 
                    messageId, userId);
                jdbcTemplate.update("UPDATE message SET love_count = love_count + 1 WHERE id = ?", messageId);
                action = "added";
            }

            // Get the complete reaction data using the same query pattern as getMessages
            String reactionDataSql = """
                SELECT 
                    m.id, m.like_count, m.love_count,
                    CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked,
                    CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved
                FROM message m
                LEFT JOIN message_reaction mr ON m.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'MESSAGE'
                LEFT JOIN message_reaction mr2 ON m.id = mr2.message_id AND mr2.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'MESSAGE'
                WHERE m.id = ?
                """;
            
            Map<String, Object> reactionData = jdbcTemplate.queryForMap(reactionDataSql, userId, userId, messageId);

            // Get families where this message is visible
            String familiesSql = "SELECT family_id FROM message_family_link WHERE message_id = ?";
            List<Map<String, Object>> families = jdbcTemplate.queryForList(familiesSql, messageId);

            // Schedule WebSocket broadcast to happen AFTER transaction commits
            final Map<String, Object> finalReactionData = new HashMap<>(reactionData);
            final Long finalMessageId = messageId;
            final String finalAction = action;
            final List<Map<String, Object>> finalFamilies = new ArrayList<>(families);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Map<String, Object> family : finalFamilies) {
                        Long familyId = ((Number) family.get("family_id")).longValue();
                        finalReactionData.put("target_type", "MESSAGE");
                        finalReactionData.put("action", finalAction);
                        finalReactionData.put("reaction_type", "LOVE");
                        webSocketBroadcastService.broadcastReaction(finalReactionData, finalMessageId, familyId);
                        logger.debug("Broadcasted message love reaction to family {} (AFTER COMMIT)", familyId);
                    }
                }
            });

            return ResponseEntity.ok(Map.of("action", action, "type", "love", "love_count", reactionData.get("love_count")));
        } catch (Exception e) {
            logger.error("Error toggling message love: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle message love: " + e.getMessage()));
        }
    }

    @PostMapping("/{commentId}/comment_like")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleCommentLike(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                       .body(Map.of("error", "User not authenticated"));
            }

            // First check if comment exists and get parent message ID
            String commentCheckSql = "SELECT id, parent_message_id FROM message_comment WHERE id = ?";
            List<Map<String, Object>> commentData = jdbcTemplate.queryForList(commentCheckSql, commentId);
            logger.info("Checking comment {}: found {} results", commentId, commentData.size());
            
            if (commentData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                       .body(Map.of("error", "Comment not found"));
            }

            Long parentMessageId = ((Number) commentData.get(0).get("parent_message_id")).longValue();

            // Check if like already exists
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'COMMENT'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, commentId, userId);
            logger.info("Checking existing reaction for comment {}: found {} results", commentId, existingReaction.size());

            String action;
            if (!existingReaction.isEmpty()) {
                // Remove like
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'COMMENT'", 
                    commentId, userId);
                logger.info("Removed like reaction for comment {}", commentId);
                    
                jdbcTemplate.update("UPDATE message_comment SET like_count = like_count - 1 WHERE id = ?", commentId);
                action = "removed";
            } else {
                // Add like
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type, target_type) VALUES (?, ?, 'LIKE', 'COMMENT')", 
                    commentId, userId);
                logger.info("Added like reaction for comment {}", commentId);

                jdbcTemplate.update("UPDATE message_comment SET like_count = like_count + 1 WHERE id = ?", commentId);
                action = "added";
            }

            // Get the complete comment reaction data using the same query pattern as getComments
            String reactionDataSql = """
                SELECT 
                    mc.id, mc.parent_message_id, mc.like_count, mc.love_count,
                    CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked,
                    CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved
                FROM message_comment mc
                LEFT JOIN message_reaction mr ON mc.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'COMMENT'
                LEFT JOIN message_reaction mr2 ON mc.id = mr2.message_id AND mr.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'COMMENT'
                WHERE mc.id = ?
                """;
            
            Map<String, Object> reactionData = jdbcTemplate.queryForMap(reactionDataSql, userId, userId, commentId);

            // Get families where the parent message is visible
            String familiesSql = "SELECT family_id FROM message_family_link WHERE message_id = ?";
            List<Map<String, Object>> families = jdbcTemplate.queryForList(familiesSql, parentMessageId);

            // Broadcast reaction update to all families
            // Schedule WebSocket broadcast to happen AFTER transaction commits
            final Map<String, Object> finalReactionData = new HashMap<>(reactionData);
            final Long finalCommentId = commentId;
            final String finalAction = action;
            final List<Map<String, Object>> finalFamilies = new ArrayList<>(families);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Map<String, Object> family : finalFamilies) {
                        Long familyId = ((Number) family.get("family_id")).longValue();
                        finalReactionData.put("target_type", "COMMENT");
                        finalReactionData.put("action", finalAction);
                        finalReactionData.put("reaction_type", "LIKE");
                        webSocketBroadcastService.broadcastReaction(finalReactionData, finalCommentId, familyId);
                        logger.debug("Broadcasted comment like reaction to family {} (AFTER COMMIT)", familyId);
                    }
                }
            });

            logger.info("Updated like count for comment {} to {}", commentId, reactionData.get("like_count"));
            return ResponseEntity.ok(Map.of("action", action, "type", "like", "like_count", reactionData.get("like_count")));
        } catch (Exception e) {
            logger.error("Error toggling comment like: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle comment like: " + e.getMessage()));
        }
    }

    @PostMapping("/{commentId}/comment_love")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleCommentLove(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                       .body(Map.of("error", "User not authenticated"));
            }

            // Get parent message ID for the comment
            String commentCheckSql = "SELECT parent_message_id FROM message_comment WHERE id = ?";
            List<Map<String, Object>> commentData = jdbcTemplate.queryForList(commentCheckSql, commentId);
            
            if (commentData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                       .body(Map.of("error", "Comment not found"));
            }

            Long parentMessageId = ((Number) commentData.get(0).get("parent_message_id")).longValue();

            // Check if love already exists
            String checkSql = "SELECT id FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'COMMENT'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, commentId, userId);

            String action;
            if (!existingReaction.isEmpty()) {
                // Remove love
                jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'COMMENT'", 
                    commentId, userId);
                
                jdbcTemplate.update("UPDATE message_comment SET love_count = love_count - 1 WHERE id = ?", commentId);
                action = "removed";
            } else {
                // Add love
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, user_id, reaction_type, target_type) VALUES (?, ?, 'LOVE', 'COMMENT')", 
                    commentId, userId);

                jdbcTemplate.update("UPDATE message_comment SET love_count = love_count + 1 WHERE id = ?", commentId);
                action = "added";
            }

            // Get the complete comment reaction data using the same query pattern as getComments
            String reactionDataSql = """
                SELECT 
                    mc.id, mc.parent_message_id, mc.like_count, mc.love_count,
                    CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked,
                    CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved
                FROM message_comment mc
                LEFT JOIN message_reaction mr ON mc.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'COMMENT'
                LEFT JOIN message_reaction mr2 ON mc.id = mr2.message_id AND mr2.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'COMMENT'
                WHERE mc.id = ?
                """;
            
            Map<String, Object> reactionData = jdbcTemplate.queryForMap(reactionDataSql, userId, userId, commentId);

            // Get families where the parent message is visible
            String familiesSql = "SELECT family_id FROM message_family_link WHERE message_id = ?";
            List<Map<String, Object>> families = jdbcTemplate.queryForList(familiesSql, parentMessageId);

            // Schedule WebSocket broadcast to happen AFTER transaction commits
            final Map<String, Object> finalReactionData = new HashMap<>(reactionData);
            final Long finalCommentId = commentId;
            final String finalAction = action;
            final List<Map<String, Object>> finalFamilies = new ArrayList<>(families);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Map<String, Object> family : finalFamilies) {
                        Long familyId = ((Number) family.get("family_id")).longValue();
                        finalReactionData.put("target_type", "COMMENT");
                        finalReactionData.put("action", finalAction);
                        finalReactionData.put("reaction_type", "LOVE");
                        webSocketBroadcastService.broadcastReaction(finalReactionData, finalCommentId, familyId);
                        logger.debug("Broadcasted comment love reaction to family {} (AFTER COMMIT)", familyId);
                    }
                }
            });

            return ResponseEntity.ok(Map.of("action", action, "type", "love", "love_count", reactionData.get("love_count")));
        } catch (Exception e) {
            logger.error("Error toggling comment love: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Failed to toggle comment love: " + e.getMessage()));
        }
    }
} 