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

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentReactionController {

    private static final Logger logger = LoggerFactory.getLogger(CommentReactionController.class);

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
    @PostMapping("/{commentId}/like")
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

            // Check if like already exists
            String checkSql = "SELECT id FROM message_reaction WHERE target_comment_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'COMMENT'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, commentId, userId);

            if (!existingReaction.isEmpty()) {
                // Remove like
                jdbcTemplate.update("DELETE FROM message_reaction WHERE target_comment_id = ? AND user_id = ? AND reaction_type = 'LIKE' AND target_type = 'COMMENT'", 
                    commentId, userId);
                jdbcTemplate.update("UPDATE message_comment SET like_count = like_count - 1 WHERE id = ?", commentId);
                return ResponseEntity.ok(Map.of("action", "removed", "type", "like"));
            } else {
                // Add like
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, target_comment_id, user_id, reaction_type, target_type) VALUES (?, ?, ?, 'LIKE', 'COMMENT')",
                    commentId, commentId, userId);
                jdbcTemplate.update("UPDATE message_comment SET like_count = like_count + 1 WHERE id = ?", commentId);
                return ResponseEntity.ok(Map.of("action", "added", "type", "like"));
            }
        } catch (Exception e) {
            logger.error("Error toggling comment like: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to toggle like: " + e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/{commentId}/love")
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

            // Check if love already exists
            String checkSql = "SELECT id FROM message_reaction WHERE target_comment_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'COMMENT'";
            List<Map<String, Object>> existingReaction = jdbcTemplate.queryForList(checkSql, commentId, userId);

            if (!existingReaction.isEmpty()) {
                // Remove love
                jdbcTemplate.update("DELETE FROM message_reaction WHERE target_comment_id = ? AND user_id = ? AND reaction_type = 'LOVE' AND target_type = 'COMMENT'", 
                    commentId, userId);
                jdbcTemplate.update("UPDATE message_comment SET love_count = love_count - 1 WHERE id = ?", commentId);
                return ResponseEntity.ok(Map.of("action", "removed", "type", "love"));
            } else {
                // Add love
                jdbcTemplate.update("INSERT INTO message_reaction (message_id, target_comment_id, user_id, reaction_type, target_type) VALUES (?, ?, ?, 'LOVE', 'COMMENT')",
                    commentId, commentId, userId);
                jdbcTemplate.update("UPDATE message_comment SET love_count = love_count + 1 WHERE id = ?", commentId);
                return ResponseEntity.ok(Map.of("action", "added", "type", "love"));
            }
        } catch (Exception e) {
            logger.error("Error toggling comment love: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to toggle love: " + e.getMessage()));
        }
    }
}

