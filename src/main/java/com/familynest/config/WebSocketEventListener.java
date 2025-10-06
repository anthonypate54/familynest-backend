package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.messaging.*;

import java.net.SocketException;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    
    // Track connection metrics instead of logging every exception
    private final AtomicLong normalDisconnects = new AtomicLong(0);
    private final AtomicLong abnormalDisconnects = new AtomicLong(0);
    private final AtomicLong connectionResets = new AtomicLong(0);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        logger.info("🔌 WebSocket CONNECT: sessionId={}", sessionId);
    }

    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        logger.info("✅ WebSocket CONNECTED: sessionId={}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        CloseStatus closeStatus = event.getCloseStatus();
        
        // Gracefully handle different types of disconnections
        if (closeStatus != null) {
            switch (closeStatus.getCode()) {
                case 1000: // Normal closure
                case 1001: // Going away (page reload, navigation)
                    normalDisconnects.incrementAndGet();
                    logger.debug("📱 WebSocket NORMAL DISCONNECT: sessionId={}, reason='{}'", 
                        sessionId, closeStatus.getReason());
                    break;
                    
                case 1006: // Abnormal closure (network issues, backgrounding)
                    abnormalDisconnects.incrementAndGet();
                    logger.debug("📱 WebSocket ABNORMAL DISCONNECT: sessionId={}, mobile_network_behavior", sessionId);
                    break;
                    
                default:
                    logger.info("❌ WebSocket DISCONNECT: sessionId={}, closeStatus={}", sessionId, closeStatus);
            }
        } else {
            logger.info("❌ WebSocket DISCONNECT: sessionId={}, closeStatus=UNKNOWN", sessionId);
        }
        
        // Log summary stats occasionally instead of every disconnect
        long totalDisconnects = normalDisconnects.get() + abnormalDisconnects.get();
        if (totalDisconnects % 50 == 0) {
            logger.info("📊 WebSocket Stats: {} normal, {} abnormal, {} connection_resets", 
                normalDisconnects.get(), abnormalDisconnects.get(), connectionResets.get());
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        logger.info("📡 WebSocket SUBSCRIBE: sessionId={}, destination={}", sessionId, destination);
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();
        logger.info("📡 WebSocket UNSUBSCRIBE: sessionId={}, subscriptionId={}", sessionId, subscriptionId);
    }
    
    /**
     * Gracefully handle transport errors without flooding logs
     */
    public void handleTransportError(String sessionId, Throwable exception) {
        if (exception instanceof SocketException) {
            String message = exception.getMessage();
            if (message != null && message.contains("Connection reset")) {
                connectionResets.incrementAndGet();
                logger.debug("📱 Mobile network behavior: Connection reset for session {}", sessionId);
                return;
            }
        }
        
        // Log unexpected transport errors
        logger.warn("⚠️ WebSocket transport error for session {}: {}", sessionId, exception.getMessage());
    }
    
    /**
     * Get current connection statistics
     */
    public String getConnectionStats() {
        return String.format("WebSocket Stats: %d normal, %d abnormal, %d connection_resets", 
            normalDisconnects.get(), abnormalDisconnects.get(), connectionResets.get());
    }
} 
