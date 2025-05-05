package com.familynest.controller;

import com.familynest.model.MessageView;
import com.familynest.model.User;
import com.familynest.service.EngagementService;
import com.familynest.repository.UserRepository;
import com.familynest.auth.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class ViewTrackingController {

    private static final Logger logger = LoggerFactory.getLogger(ViewTrackingController.class);

    @Autowired
    private EngagementService engagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/{messageId}/views")
    public ResponseEntity<Map<String, Object>> markMessageAsViewed(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Marking message ID: {} as viewed", messageId);
        
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
            
            MessageView view = engagementService.markMessageAsViewed(messageId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", view.getId());
            response.put("messageId", view.getMessageId());
            response.put("userId", view.getUserId());
            response.put("viewedAt", view.getViewedAt());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Error marking message as viewed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error marking message as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark message as viewed: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/views")
    public ResponseEntity<Map<String, Object>> getMessageViews(@PathVariable Long messageId) {
        logger.debug("Getting views for message ID: {}", messageId);
        
        try {
            List<MessageView> views = engagementService.getMessageViews(messageId);
            long viewCount = views.size();
            
            // Get user details for each view
            List<Map<String, Object>> viewDetails = new ArrayList<>();
            for (MessageView view : views) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", view.getId());
                detail.put("userId", view.getUserId());
                detail.put("viewedAt", view.getViewedAt());
                
                // Add basic user info
                User user = userRepository.findById(view.getUserId()).orElse(null);
                if (user != null) {
                    detail.put("username", user.getUsername());
                    detail.put("firstName", user.getFirstName());
                    detail.put("lastName", user.getLastName());
                    detail.put("photo", user.getPhoto());
                }
                
                viewDetails.add(detail);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("views", viewDetails);
            response.put("viewCount", viewCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting message views: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get message views: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/views/check")
    public ResponseEntity<Map<String, Object>> checkIfMessageViewed(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        logger.debug("Checking if message ID: {} has been viewed", messageId);
        
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
            
            boolean isViewed = engagementService.isMessageViewedByUser(messageId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("viewed", isViewed);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking if message viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check if message viewed: " + e.getMessage()));
        }
    }

    @GetMapping("/{messageId}/engagement")
    public ResponseEntity<Map<String, Object>> getMessageEngagementData(
            @PathVariable Long messageId,
            HttpServletRequest request) {
        logger.debug("Getting engagement data for message ID: {}", messageId);
        
        try {
            // For testing, add the viewed status based on the current test user
            Map<String, Object> engagementData = engagementService.getMessageEngagementData(messageId);
            
            // If in test environment, check if the test user has viewed the message
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                Long userId = (Long) userIdAttr;
                boolean isViewed = engagementService.isMessageViewedByUser(messageId, userId);
                engagementData.put("viewed", isViewed);
                logger.debug("Added viewed status for test user: {} = {}", userId, isViewed);
            }
            
            return ResponseEntity.ok(engagementData);
        } catch (Exception e) {
            logger.error("Error getting message engagement data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get message engagement data: " + e.getMessage()));
        }
    }
} 