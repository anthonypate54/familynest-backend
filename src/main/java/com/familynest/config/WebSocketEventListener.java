package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;
import com.familynest.service.WebSocketSessionCleanupService;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    
    @Autowired
    private WebSocketSessionCleanupService cleanupService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);
        
        logger.info("🔌 WebSocket CONNECT: sessionId={}, userId={}", sessionId, userId);
        
        if (userId != null) {
            cleanupService.recordSessionConnect(sessionId, userId);
        }
    }

    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);
        
        logger.info("✅ WebSocket CONNECTED: sessionId={}, userId={}", sessionId, userId);
        
        if (userId != null) {
            cleanupService.recordSessionActivity(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String closeStatus = event.getCloseStatus() != null ? event.getCloseStatus().toString() : "UNKNOWN";
        
        logger.info("❌ WebSocket DISCONNECT: sessionId={}, closeStatus={}", sessionId, closeStatus);
        
        cleanupService.recordSessionDisconnect(sessionId);
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        String userId = extractUserId(headerAccessor);
        
        logger.info("📡 WebSocket SUBSCRIBE: sessionId={}, destination={}, userId={}", sessionId, destination, userId);
        
        if (userId != null) {
            cleanupService.recordSessionActivity(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();
        String userId = extractUserId(headerAccessor);
        
        logger.info("📡 WebSocket UNSUBSCRIBE: sessionId={}, subscriptionId={}, userId={}", sessionId, subscriptionId, userId);
        
        if (userId != null) {
            cleanupService.recordSessionActivity(sessionId);
        }
    }

    /**
     * Extract user ID from WebSocket headers
     * This should be enhanced based on your authentication mechanism
     */
    private String extractUserId(StompHeaderAccessor headerAccessor) {
        // Try to get user from principal
        if (headerAccessor.getUser() != null) {
            return headerAccessor.getUser().getName();
        }
        
        // Try to get user from session attributes
        if (headerAccessor.getSessionAttributes() != null) {
            Object userId = headerAccessor.getSessionAttributes().get("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        // Try to get user from native headers
        if (headerAccessor.getNativeHeader("userId") != null) {
            return headerAccessor.getNativeHeader("userId").get(0);
        }
        
        return null;
    }
} 