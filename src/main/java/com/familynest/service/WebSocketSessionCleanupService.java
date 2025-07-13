package com.familynest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WebSocketSessionCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionCleanupService.class);
    
    private final SimpUserRegistry simpUserRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Track session activity
    private final ConcurrentHashMap<String, LocalDateTime> sessionActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);
    private final AtomicInteger totalSessionsCreated = new AtomicInteger(0);
    
    // Configuration
    private static final long SESSION_TIMEOUT_MINUTES = 30;
    private static final long CLEANUP_INTERVAL_MINUTES = 5;
    private static final long HEALTH_CHECK_INTERVAL_MINUTES = 2;
    
    public WebSocketSessionCleanupService(SimpUserRegistry simpUserRegistry, SimpMessagingTemplate messagingTemplate) {
        this.simpUserRegistry = simpUserRegistry;
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Record session activity when a user connects
     */
    public void recordSessionConnect(String sessionId, String userId) {
        sessionActivity.put(sessionId, LocalDateTime.now());
        sessionToUserId.put(sessionId, userId);
        totalSessionsCreated.incrementAndGet();
        activeSessionCount.incrementAndGet();
        
        logger.info("🔌 Session CONNECT: sessionId={}, userId={}, activeCount={}", 
                   sessionId, userId, activeSessionCount.get());
    }
    
    /**
     * Record session activity when a user disconnects
     */
    public void recordSessionDisconnect(String sessionId) {
        sessionActivity.remove(sessionId);
        String userId = sessionToUserId.remove(sessionId);
        activeSessionCount.decrementAndGet();
        
        logger.info("❌ Session DISCONNECT: sessionId={}, userId={}, activeCount={}", 
                   sessionId, userId, activeSessionCount.get());
    }
    
    /**
     * Record session activity (heartbeat, message received, etc.)
     */
    public void recordSessionActivity(String sessionId) {
        sessionActivity.put(sessionId, LocalDateTime.now());
    }
    
    /**
     * Handle ping message to keep session alive
     */
    public void handlePing(String sessionId, String userId) {
        recordSessionActivity(sessionId);
        
        // Send pong response
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/pong", 
                new PongResponse(LocalDateTime.now().toString()));
            logger.debug("🏓 PONG sent to user: {}", userId);
        } catch (Exception e) {
            logger.error("❌ Error sending pong to user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Periodic cleanup of stale sessions
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MINUTES * 60 * 1000)
    public void cleanupStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minus(SESSION_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        int removedCount = 0;
        
        // Find and remove stale sessions
        var iterator = sessionActivity.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String sessionId = entry.getKey();
            LocalDateTime lastActivity = entry.getValue();
            
            if (lastActivity.isBefore(cutoff)) {
                iterator.remove();
                String userId = sessionToUserId.remove(sessionId);
                activeSessionCount.decrementAndGet();
                removedCount++;
                
                logger.info("🧹 Cleaned up stale session: sessionId={}, userId={}, lastActivity={}", 
                           sessionId, userId, lastActivity);
            }
        }
        
        if (removedCount > 0) {
            logger.info("🧹 Session cleanup completed: removed {} stale sessions, active count: {}", 
                       removedCount, activeSessionCount.get());
        }
    }
    
    /**
     * Health monitoring and logging
     * Runs every 2 minutes
     */
    @Scheduled(fixedRate = HEALTH_CHECK_INTERVAL_MINUTES * 60 * 1000)
    public void logHealthStatus() {
        int registryUserCount = simpUserRegistry.getUserCount();
        int activeSessionsTracked = activeSessionCount.get();
        int totalSessions = totalSessionsCreated.get();
        
        logger.info("💓 WebSocket Health: Registry users={}, Active sessions={}, Total sessions created={}", 
                   registryUserCount, activeSessionsTracked, totalSessions);
        
        // Check for discrepancies
        if (Math.abs(registryUserCount - activeSessionsTracked) > 5) {
            logger.warn("⚠️ Session count discrepancy: Registry={}, Tracked={}", 
                       registryUserCount, activeSessionsTracked);
        }
        
        // Log oldest session activity
        if (!sessionActivity.isEmpty()) {
            LocalDateTime oldestActivity = sessionActivity.values().stream()
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
            
            long minutesSinceOldest = ChronoUnit.MINUTES.between(oldestActivity, LocalDateTime.now());
            logger.info("📊 Oldest session activity: {} minutes ago", minutesSinceOldest);
        }
    }
    
    /**
     * Get current session statistics
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            activeSessionCount.get(),
            totalSessionsCreated.get(),
            sessionActivity.size(),
            simpUserRegistry.getUserCount()
        );
    }
    
    /**
     * Force cleanup of all sessions for a specific user
     */
    public void forceCleanupUser(String userId) {
        int removedCount = 0;
        
        var iterator = sessionToUserId.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (userId.equals(entry.getValue())) {
                String sessionId = entry.getKey();
                iterator.remove();
                sessionActivity.remove(sessionId);
                activeSessionCount.decrementAndGet();
                removedCount++;
                
                logger.info("🧹 Force cleaned user session: sessionId={}, userId={}", sessionId, userId);
            }
        }
        
        logger.info("🧹 Force cleanup completed for user {}: removed {} sessions", userId, removedCount);
    }
    
    /**
     * Response object for pong messages
     */
    public static class PongResponse {
        private final String timestamp;
        private final String type = "PONG";
        
        public PongResponse(String timestamp) {
            this.timestamp = timestamp;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public String getType() {
            return type;
        }
    }
    
    /**
     * Statistics object for session monitoring
     */
    public static class SessionStats {
        private final int activeSessionCount;
        private final int totalSessionsCreated;
        private final int trackedSessionCount;
        private final int registryUserCount;
        
        public SessionStats(int activeSessionCount, int totalSessionsCreated, 
                           int trackedSessionCount, int registryUserCount) {
            this.activeSessionCount = activeSessionCount;
            this.totalSessionsCreated = totalSessionsCreated;
            this.trackedSessionCount = trackedSessionCount;
            this.registryUserCount = registryUserCount;
        }
        
        // Getters
        public int getActiveSessionCount() { return activeSessionCount; }
        public int getTotalSessionsCreated() { return totalSessionsCreated; }
        public int getTrackedSessionCount() { return trackedSessionCount; }
        public int getRegistryUserCount() { return registryUserCount; }
    }
} 