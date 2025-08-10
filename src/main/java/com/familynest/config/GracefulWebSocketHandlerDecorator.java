package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.net.SocketException;

/**
 * WebSocket handler decorator that gracefully handles mobile network connection resets
 * without flooding logs with expected exceptions
 */
public class GracefulWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    private static final Logger logger = LoggerFactory.getLogger(GracefulWebSocketHandlerDecorator.class);
    private final WebSocketEventListener eventListener;

    public GracefulWebSocketHandlerDecorator(WebSocketHandler delegate, WebSocketEventListener eventListener) {
        super(delegate);
        this.eventListener = eventListener;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        
        // Use our graceful error handling
        eventListener.handleTransportError(sessionId, exception);
        
        // Still delegate to the original handler for proper cleanup
        try {
            super.handleTransportError(session, exception);
        } catch (Exception e) {
            // If the delegate also throws, handle gracefully
            if (!(e instanceof SocketException && e.getMessage() != null && e.getMessage().contains("Connection reset"))) {
                logger.warn("⚠️ Error in WebSocket delegate handler for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.debug("🔌 WebSocket connection closed: sessionId={}, status={}", session.getId(), closeStatus);
        super.afterConnectionClosed(session, closeStatus);
    }
}