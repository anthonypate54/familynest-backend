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
            -- Removed message_view table references
            WHERE ufm.user_id = ?
            AND m.sender_id != ?
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
            -- Removed message_view table references
            WHERE (dc.user1_id = ? OR dc.user2_id = ?)
            AND dm.sender_id != ?
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
    
    /**
     * Send family invitation email with app download instructions and privacy messaging
     */
    public void sendFamilyInvitationEmail(String inviteeEmail, String familyName, String inviterName, String invitationToken) {
        try {
            logger.info("Sending family invitation email to: {} for family: {}", inviteeEmail, familyName);
            
            // Create HTML email content
            String htmlContent = createFamilyInvitationHtml(inviteeEmail, familyName, inviterName, invitationToken);
            
            // Send HTML email
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(inviteeEmail);
            helper.setSubject("🏠 " + inviterName + " invited you to join " + familyName + " on FamilyNest!");
            helper.setText(htmlContent, true); // true = HTML content
            helper.setFrom("FamilyNest <anthonypate@gmail.com>");
            
            mailSender.send(mimeMessage);
            logger.info("Family invitation email sent successfully to: {}", inviteeEmail);
            
        } catch (MessagingException e) {
            logger.error("Failed to create family invitation email for {}: {}", inviteeEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to create family invitation email", e);
        } catch (Exception e) {
            logger.error("Failed to send family invitation email to {}: {}", inviteeEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send family invitation email", e);
        }
    }
    
    /**
     * Create HTML content for family invitation email
     */
    private String createFamilyInvitationHtml(String inviteeEmail, String familyName, String inviterName, String invitationToken) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>")
            .append("<html><head>")
            .append("<meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            .append("<style>")
            .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f5f5f5; }")
            .append(".container { max-width: 600px; margin: 0 auto; background: white; }")
            .append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 30px; text-align: center; }")
            .append(".header h1 { margin: 0; font-size: 28px; font-weight: 300; }")
            .append(".header p { margin: 10px 0 0 0; font-size: 16px; opacity: 0.9; }")
            .append(".content { padding: 40px 30px; }")
            .append(".welcome { text-align: center; margin-bottom: 40px; }")
            .append(".welcome h2 { color: #4a5568; font-size: 24px; margin-bottom: 10px; }")
            .append(".family-name { color: #667eea; font-weight: bold; }")
            .append(".download-section { background: #f8faff; border-radius: 12px; padding: 30px; margin: 30px 0; text-align: center; border: 1px solid #e2e8f0; }")
            .append(".download-buttons { margin: 20px 0; }")
            .append(".download-btn { display: inline-block; margin: 10px; padding: 12px 24px; background: #333; color: white; text-decoration: none; border-radius: 8px; font-weight: 500; }")
            .append(".download-btn:hover { background: #555; }")
            .append(".ios-btn { background: #007AFF; }")
            .append(".android-btn { background: #34A853; }")
            .append(".steps { margin: 30px 0; }")
            .append(".step { display: flex; align-items: flex-start; margin: 20px 0; }")
            .append(".step-number { background: #667eea; color: white; width: 24px; height: 24px; border-radius: 50%; display: table-cell; vertical-align: middle; text-align: center; font-weight: bold; margin-right: 15px; flex-shrink: 0; line-height: 24px; font-size: 12px; }")
            .append(".step-content { flex: 1; }")
            .append(".step h3 { margin: 0 0 5px 0; color: #2d3748; font-size: 16px; }")
            .append(".step p { margin: 0; color: #718096; font-size: 14px; }")
            .append(".privacy-section { background: #f0fff4; border-left: 4px solid #48bb78; padding: 20px; margin: 30px 0; border-radius: 0 8px 8px 0; }")
            .append(".privacy-section h3 { color: #2f855a; margin: 0 0 10px 0; }")
            .append(".privacy-points { margin: 15px 0; }")
            .append(".privacy-point { margin: 8px 0; color: #2d3748; }")
            .append(".privacy-point::before { content: '✓'; color: #48bb78; font-weight: bold; margin-right: 8px; }")
            .append(".footer { background: #f7fafc; padding: 30px; text-align: center; color: #718096; font-size: 14px; }")
            .append(".footer a { color: #667eea; text-decoration: none; }")
            .append("@media (max-width: 600px) { .content, .header { padding: 20px; } .download-btn { display: block; margin: 10px 0; } }")
            .append("</style>")
            .append("</head><body>")
            .append("<div class='container'>");
        
        // Header
        html.append("<div class='header'>")
            .append("<h1>🏠 Welcome to FamilyNest</h1>")
            .append("<p>Private, secure family communication</p>")
            .append("</div>");
        
        // Welcome Section
        html.append("<div class='content'>")
            .append("<div class='welcome'>")
            .append("<h2>You're invited to join<br><span class='family-name'>").append(familyName).append("</span></h2>")
            .append("<p><strong>").append(inviterName).append("</strong> has invited you to connect with your family on FamilyNest - ")
            .append("a private communication app designed specifically for families.</p>")
            .append("</div>");
        
        // Download Section
        html.append("<div class='download-section'>")
            .append("<h3>📱 Step 1: Download FamilyNest</h3>")
            .append("<p>Get the app on your device:</p>")
            .append("<div class='download-buttons'>")
            .append("<a href='https://apps.apple.com/app/familynest' class='download-btn ios-btn'>📱 Download for iPhone</a>")
            .append("<a href='https://play.google.com/store/apps/details?id=com.familynest' class='download-btn android-btn'>🤖 Download for Android</a>")
            .append("</div>")
            .append("<p><small>Available on both iOS and Android</small></p>")
            .append("</div>");
        
        // Steps Section
        html.append("<div class='steps'>")
            .append("<h3>✨ Getting Started is Easy:</h3>")
            
            .append("<div class='step'>")
            .append("<div class='step-number'>1</div>")
            .append("<div class='step-content'>")
            .append("<h3>Download the app</h3>")
            .append("<p>Use the buttons above to get FamilyNest from your app store</p>")
            .append("</div></div>")
            
            .append("<div class='step'>")
            .append("<div class='step-number'>2</div>")
            .append("<div class='step-content'>")
            .append("<h3>Sign up with this email</h3>")
            .append("<p>Use <strong>").append(inviteeEmail).append("</strong> when creating your account</p>")
            .append("</div></div>")
            
            .append("<div class='step'>")
            .append("<div class='step-number'>3</div>")
            .append("<div class='step-content'>")
            .append("<h3>Your invitation will be waiting!</h3>")
            .append("<p>Once signed up, you'll automatically see your invitation to join <strong>").append(familyName).append("</strong></p>")
            .append("</div></div>")
            .append("</div>");
        
        // Privacy Section
        html.append("<div class='privacy-section'>")
            .append("<h3>🔒 Why Choose FamilyNest?</h3>")
            .append("<p>Unlike other messaging apps, FamilyNest puts your family's privacy first:</p>")
            .append("<div class='privacy-points'>")
            .append("<div class='privacy-point'>Your conversations are NOT used for advertising</div>")
            .append("<div class='privacy-point'>No data mining or selling your family's information</div>")
            .append("<div class='privacy-point'>Built specifically for families, not corporations</div>")
            .append("<div class='privacy-point'>End-to-end security for your family moments</div>")
            .append("<div class='privacy-point'>Ad-free experience - your family time is precious</div>")
            .append("</div>")
            .append("<p><strong>For less than a cup of coffee per month, protect your family's digital privacy.</strong></p>")
            .append("</div>");
        
        // Footer
        html.append("</div>")
            .append("<div class='footer'>")
            .append("<p>Questions? Reply to this email or visit <a href='https://familynest.app/help'>our help center</a></p>")
            .append("<p>You're receiving this because ").append(inviterName).append(" invited you to join their family.</p>")
            .append("<p><small>FamilyNest - Keeping families connected, privately.</small></p>")
            .append("</div>");
        
        html.append("</div></body></html>");
        
        return html.toString();
    }
} 