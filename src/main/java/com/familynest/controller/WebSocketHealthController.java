package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.familynest.service.WebSocketSessionCleanupService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/websocket")
public class WebSocketHealthController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHealthController.class);
    
    @Autowired
    private WebSocketSessionCleanupService cleanupService;

    /**
     * Handle ping messages from clients
     */
    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public WebSocketSessionCleanupService.PongResponse handlePing(
            @Payload Map<String, Object> pingMessage,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {
        
        String sessionId = headerAccessor.getSessionId();
        String userId = principal != null ? principal.getName() : "anonymous";
        
        logger.debug("🏓 PING received from user: {}, sessionId: {}", userId, sessionId);
        
        // Record activity and get pong response
        cleanupService.recordSessionActivity(sessionId);
        
        return new WebSocketSessionCleanupService.PongResponse(
            java.time.LocalDateTime.now().toString()
        );
    }
    
    /**
     * Get WebSocket session statistics (for monitoring/debugging)
     */
    @GetMapping("/stats")
    public ResponseEntity<WebSocketSessionCleanupService.SessionStats> getSessionStats() {
        try {
            WebSocketSessionCleanupService.SessionStats stats = cleanupService.getSessionStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting session stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Force cleanup sessions for a specific user (admin endpoint)
     */
    @PostMapping("/cleanup/{userId}")
    public ResponseEntity<String> forceCleanupUser(@PathVariable String userId) {
        try {
            cleanupService.forceCleanupUser(userId);
            return ResponseEntity.ok("Sessions cleaned up for user: " + userId);
        } catch (Exception e) {
            logger.error("Error cleaning up user sessions: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error cleaning up sessions");
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            WebSocketSessionCleanupService.SessionStats stats = cleanupService.getSessionStats();
            
            Map<String, Object> health = Map.of(
                "status", "healthy",
                "activeSessions", stats.getActiveSessionCount(),
                "totalSessions", stats.getTotalSessionsCreated(),
                "registryUsers", stats.getRegistryUserCount(),
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        }
    }
} 