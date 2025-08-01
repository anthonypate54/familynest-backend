package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            logger.info("Sending password reset email to: {}", toEmail);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("FamilyNest - Password Reset Request");
            message.setText(
                "Hello,\n\n" +
                "You have requested to reset your password for your FamilyNest account.\n\n" +
                "Please click the following link to reset your password:\n" +
                "http://localhost:8080/reset-password?token=" + resetToken + "\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "If you did not request this password reset, please ignore this email.\n\n" +
                "Best regards,\n" +
                "The FamilyNest Team"
            );
            
            mailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
    
    public void sendUsernameReminderEmail(String toEmail, String username) {
        try {
            logger.info("Sending username reminder email to: {}", toEmail);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("FamilyNest - Username Reminder");
            message.setText(
                "Hello,\n\n" +
                "You have requested a username reminder for your FamilyNest account.\n\n" +
                "Your username is: " + username + "\n\n" +
                "You can use this username to login to your FamilyNest account.\n\n" +
                "If you did not request this username reminder, please ignore this email.\n\n" +
                "Best regards,\n" +
                "The FamilyNest Team"
            );
            
            mailSender.send(message);
            logger.info("Username reminder email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send username reminder email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send username reminder email", e);
        }
    }

    /**
     * Send daily digest email with unread messages summary
     */
    public void sendDailyDigest(Long userId, String userEmail, String userName) {
        try {
            logger.info("Preparing daily digest email for user: {} ({})", userName, userEmail);
            
            // Get unread family messages
            List<Map<String, Object>> unreadFamilyMessages = getUnreadFamilyMessages(userId);
            
            // Get unread DM messages
            List<Map<String, Object>> unreadDMMessages = getUnreadDMMessages(userId);
            
            // Only send email if there are unread messages
            if (unreadFamilyMessages.isEmpty() && unreadDMMessages.isEmpty()) {
                logger.debug("No unread messages for user {}, skipping daily digest", userName);
                return;
            }
            
            // Create HTML email content
            String htmlContent = createDailyDigestHtml(userName, unreadFamilyMessages, unreadDMMessages);
            
            // Send HTML email
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(userEmail);
            helper.setSubject("FamilyNest Daily Digest - " + formatCurrentDate());
            helper.setText(htmlContent, true); // true = HTML content
            
            mailSender.send(mimeMessage);
            logger.info("Daily digest email sent successfully to: {}", userEmail);
            
        } catch (MessagingException e) {
            logger.error("Failed to create daily digest email for {}: {}", userEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to create daily digest email", e);
        } catch (Exception e) {
            logger.error("Failed to send daily digest email to {}: {}", userEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send daily digest email", e);
        }
    }
    
    /**
     * Get unread family messages for a user (last 24 hours)
     */
    private List<Map<String, Object>> getUnreadFamilyMessages(Long userId) {
        String sql = """
            SELECT 
                f.name as family_name,
                m.content,
                m.sender_username,
                m.timestamp,
                COUNT(*) OVER (PARTITION BY f.id) as family_unread_count
            FROM message m
            JOIN family f ON m.family_id = f.id
            JOIN user_family_membership ufm ON f.id = ufm.family_id
            LEFT JOIN message_view mv ON (m.id = mv.message_id AND mv.user_id = ? AND mv.message_type = 'family')
            WHERE ufm.user_id = ?
            AND m.sender_id != ?
            AND mv.id IS NULL
            AND m.timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
            ORDER BY f.name, m.timestamp DESC
            LIMIT 20
        """;
        
        return jdbcTemplate.queryForList(sql, userId, userId, userId);
    }
    
    /**
     * Get unread DM messages for a user (last 24 hours)
     */
    private List<Map<String, Object>> getUnreadDMMessages(Long userId) {
        String sql = """
            SELECT 
                CASE 
                    WHEN dm.sender_id = dc.user1_id THEN COALESCE(u1.first_name || ' ' || u1.last_name, u1.username)
                    ELSE COALESCE(u2.first_name || ' ' || u2.last_name, u2.username)
                END as sender_name,
                dm.content,
                dm.created_at as timestamp,
                COUNT(*) OVER () as total_dm_count
            FROM dm_message dm
            JOIN dm_conversation dc ON dm.conversation_id = dc.id
            JOIN app_user u1 ON dc.user1_id = u1.id
            JOIN app_user u2 ON dc.user2_id = u2.id
            LEFT JOIN message_view mv ON (dm.id = mv.dm_message_id AND mv.user_id = ? AND mv.message_type = 'dm')
            WHERE (dc.user1_id = ? OR dc.user2_id = ?)
            AND dm.sender_id != ?
            AND mv.id IS NULL
            AND dm.created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
            ORDER BY dm.created_at DESC
            LIMIT 10
        """;
        
        return jdbcTemplate.queryForList(sql, userId, userId, userId, userId);
    }
    
    /**
     * Create HTML content for daily digest email
     */
    private String createDailyDigestHtml(String userName, 
                                       List<Map<String, Object>> familyMessages, 
                                       List<Map<String, Object>> dmMessages) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>")
            .append("<html><head>")
            .append("<meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }")
            .append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }")
            .append(".header { background: #4CAF50; color: white; padding: 20px; text-align: center; }")
            .append(".section { margin: 20px 0; padding: 15px; border-left: 4px solid #4CAF50; }")
            .append(".message { background: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 5px; }")
            .append(".sender { font-weight: bold; color: #4CAF50; }")
            .append(".timestamp { font-size: 0.8em; color: #666; }")
            .append(".footer { text-align: center; color: #666; font-size: 0.9em; margin-top: 30px; }")
            .append("</style>")
            .append("</head><body>")
            .append("<div class='container'>");
        
        // Header
        html.append("<div class='header'>")
            .append("<h1>🏠 FamilyNest Daily Digest</h1>")
            .append("<p>Hello ").append(userName).append("!</p>")
            .append("</div>");
        
        // Family Messages Section
        if (!familyMessages.isEmpty()) {
            html.append("<div class='section'>")
                .append("<h2>📱 Family Messages (").append(familyMessages.size()).append(" new)</h2>");
            
            String currentFamily = "";
            for (Map<String, Object> msg : familyMessages) {
                String familyName = (String) msg.get("family_name");
                if (!familyName.equals(currentFamily)) {
                    if (!currentFamily.isEmpty()) html.append("</div>");
                    html.append("<h3>👨‍👩‍👧‍👦 ").append(familyName).append("</h3>");
                    currentFamily = familyName;
                }
                
                html.append("<div class='message'>")
                    .append("<div class='sender'>").append(msg.get("sender_username")).append("</div>")
                    .append("<div>").append(truncateContent((String) msg.get("content"))).append("</div>")
                    .append("<div class='timestamp'>").append(formatTimestamp(msg.get("timestamp"))).append("</div>")
                    .append("</div>");
            }
            html.append("</div>");
        }
        
        // DM Messages Section
        if (!dmMessages.isEmpty()) {
            html.append("<div class='section'>")
                .append("<h2>💬 Direct Messages (").append(dmMessages.size()).append(" new)</h2>");
            
            for (Map<String, Object> msg : dmMessages) {
                html.append("<div class='message'>")
                    .append("<div class='sender'>").append(msg.get("sender_name")).append("</div>")
                    .append("<div>").append(truncateContent((String) msg.get("content"))).append("</div>")
                    .append("<div class='timestamp'>").append(formatTimestamp(msg.get("timestamp"))).append("</div>")
                    .append("</div>");
            }
            html.append("</div>");
        }
        
        // Footer
        html.append("<div class='footer'>")
            .append("<p>Open the FamilyNest app to read and respond to these messages.</p>")
            .append("<p>You're receiving this because you have email notifications enabled.</p>")
            .append("<p><small>FamilyNest - Keeping families connected</small></p>")
            .append("</div>");
        
        html.append("</div></body></html>");
        
        return html.toString();
    }
    
    /**
     * Truncate message content for email preview
     */
    private String truncateContent(String content) {
        if (content == null) return "";
        if (content.length() <= 100) return content;
        return content.substring(0, 97) + "...";
    }
    
    /**
     * Format timestamp for email display
     */
    private String formatTimestamp(Object timestamp) {
        if (timestamp instanceof LocalDateTime) {
            return ((LocalDateTime) timestamp).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        }
        return timestamp.toString();
    }
    
    /**
     * Format current date for email subject
     */
    private String formatCurrentDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
    }
} 