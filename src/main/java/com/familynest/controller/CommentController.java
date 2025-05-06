package com.familynest.controller;

import com.familynest.model.MessageComment;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Get comments for a message with pagination
     */
    @GetMapping("/{messageId}/comments")
    public ResponseEntity<Map<String, Object>> getComments(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        logger.debug("Getting comments for message ID: {}", messageId);
        
        try {
            Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            Page<MessageComment> commentsPage = engagementService.getMessageComments(messageId, pageable);
            List<MessageComment> comments = commentsPage.getContent();
            
            // Get user details for each comment
            List<Map<String, Object>> commentDetails = new ArrayList<>();
            for (MessageComment comment : comments) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", comment.getId());
                detail.put("messageId", comment.getMessageId());
                detail.put("userId", comment.getUserId());
                detail.put("content", comment.getContent());
                detail.put("mediaUrl", comment.getMediaUrl());
                detail.put("mediaType", comment.getMediaType());
                detail.put("createdAt", comment.getCreatedAt());
                detail.put("updatedAt", comment.getUpdatedAt());
                detail.put("parentCommentId", comment.getParentCommentId());
                
                // Add reply count
                long replyCount = engagementService.getCommentReplyCount(comment.getId());
                detail.put("replyCount", replyCount);
                
                // Add basic user info
                User user = userRepository.findById(comment.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                commentDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("comments", commentDetails);
            response.put("currentPage", commentsPage.getNumber());
            response.put("totalItems", commentsPage.getTotalElements());
            response.put("totalPages", commentsPage.getTotalPages());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting comments: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get comments: " + e.getMessage()));
        }
    }
    
    /**
     * Get replies for a comment
     */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<Map<String, Object>> getCommentReplies(@PathVariable Long commentId) {
        logger.debug("Getting replies for comment ID: {}", commentId);
        
        try {
            List<MessageComment> replies = engagementService.getCommentReplies(commentId);
            
            // Get user details for each reply
            List<Map<String, Object>> replyDetails = new ArrayList<>();
            for (MessageComment reply : replies) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", reply.getId());
                detail.put("messageId", reply.getMessageId());
                detail.put("userId", reply.getUserId());
                detail.put("content", reply.getContent());
                detail.put("mediaUrl", reply.getMediaUrl());
                detail.put("mediaType", reply.getMediaType());
                detail.put("createdAt", reply.getCreatedAt());
                detail.put("updatedAt", reply.getUpdatedAt());
                detail.put("parentCommentId", reply.getParentCommentId());
                
                // Add basic user info
                User user = userRepository.findById(reply.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                replyDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("replies", replyDetails);
            response.put("count", replies.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting comment replies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get comment replies: " + e.getMessage()));
        }
    }
    
    /**
     * Add a new comment to a message
     */
    @PostMapping("/{messageId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> commentData,
            HttpServletRequest request) {
        logger.debug("Adding comment to message ID: {}", messageId);
        
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
            
            String content = (String) commentData.get("content");
            if (content == null || content.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comment content is required"));
            }
            
            // Check if this is a reply to another comment
            Long parentCommentId = null;
            if (commentData.containsKey("parentCommentId") && commentData.get("parentCommentId") != null) {
                parentCommentId = Long.valueOf(commentData.get("parentCommentId").toString());
            }
            
            MessageComment comment = engagementService.addComment(messageId, userId, content, parentCommentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", comment.getId());
            response.put("messageId", comment.getMessageId());
            response.put("userId", comment.getUserId());
            response.put("content", comment.getContent());
            response.put("createdAt", comment.getCreatedAt());
            response.put("parentCommentId", comment.getParentCommentId());
            
            // Add user details
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                response.put("username", user.getUsername());
                response.put("photo", user.getPhoto());
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error adding comment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error adding comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add comment: " + e.getMessage()));
        }
    }
    
    /**
     * Update an existing comment
     */
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> commentData,
            HttpServletRequest request) {
        logger.debug("Updating comment ID: {}", commentId);
        
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
            
            String content = commentData.get("content");
            if (content == null || content.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comment content is required"));
            }
            
            MessageComment updatedComment = engagementService.updateComment(commentId, userId, content);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComment.getId());
            response.put("messageId", updatedComment.getMessageId());
            response.put("userId", updatedComment.getUserId());
            response.put("content", updatedComment.getContent());
            response.put("createdAt", updatedComment.getCreatedAt());
            response.put("updatedAt", updatedComment.getUpdatedAt());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error updating comment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update comment: " + e.getMessage()));
        }
    }
    
    /**
     * Delete a comment
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Deleting comment ID: {}", commentId);
        
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
            
            engagementService.deleteComment(commentId, userId);
            
            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.error("Error deleting comment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting comment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete comment: " + e.getMessage()));
        }
    }
} 