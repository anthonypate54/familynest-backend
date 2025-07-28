package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class DailyDigestScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(DailyDigestScheduler.class);
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Send daily digest emails at 8:00 AM every day
     * Cron expression: "0 0 8 * * *" = second minute hour day month dayOfWeek
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyDigests() {
        logger.info("Starting daily digest email job");
        
        try {
            // Get all users who have email notifications enabled
            String sql = """
                SELECT u.id, u.email, COALESCE(u.first_name || ' ' || u.last_name, u.username) as display_name
                FROM app_user u
                JOIN user_notification_settings uns ON u.id = uns.user_id
                WHERE uns.email_notifications_enabled = true
                AND u.email IS NOT NULL
                AND u.email != ''
                ORDER BY u.id
            """;
            
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql);
            logger.info("Found {} users with email notifications enabled", users.size());
            
            int successCount = 0;
            int errorCount = 0;
            
            for (Map<String, Object> user : users) {
                try {
                    Long userId = (Long) user.get("id");
                    String email = (String) user.get("email");
                    String displayName = (String) user.get("display_name");
                    
                    emailService.sendDailyDigest(userId, email, displayName);
                    successCount++;
                    
                    // Small delay to avoid overwhelming the email server
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    errorCount++;
                    logger.error("Failed to send daily digest to user {}: {}", 
                               user.get("email"), e.getMessage());
                }
            }
            
            logger.info("Daily digest job completed. Success: {}, Errors: {}", successCount, errorCount);
            
        } catch (Exception e) {
            logger.error("Daily digest job failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Manual trigger for testing (can be called from a controller if needed)
     */
    public void sendDailyDigestsNow() {
        logger.info("Manual daily digest trigger");
        sendDailyDigests();
    }
    
    /**
     * Send digest to a specific user (for testing)
     */
    public void sendDigestToUser(Long userId) {
        try {
            String sql = """
                SELECT u.id, u.email, COALESCE(u.first_name || ' ' || u.last_name, u.username) as display_name
                FROM app_user u
                WHERE u.id = ?
            """;
            
            Map<String, Object> user = jdbcTemplate.queryForMap(sql, userId);
            String email = (String) user.get("email");
            String displayName = (String) user.get("display_name");
            
            emailService.sendDailyDigest(userId, email, displayName);
            logger.info("Test digest sent to user {} ({})", displayName, email);
            
        } catch (Exception e) {
            logger.error("Failed to send test digest to user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to send test digest", e);
        }
    }
} 