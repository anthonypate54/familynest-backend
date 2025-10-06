package com.familynest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
public class SESEmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(SESEmailService.class);
    
    @Value("${aws.ses.region:us-west-2}")
    private String awsRegion;
    
    @Value("${aws.ses.access-key:}")
    private String accessKey;
    
    @Value("${aws.ses.secret-key:}")
    private String secretKey;
    
    @Value("${aws.ses.from-email:noreply@infamilynest.com}")
    private String fromEmail;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    private SesClient sesClient;
    
    /**
     * Initialize SES client with credentials
     */
    private SesClient getSesClient() {
        if (sesClient == null) {
            if (accessKey.isEmpty() || secretKey.isEmpty()) {
                throw new RuntimeException("AWS SES credentials not configured. Set aws.ses.access-key and aws.ses.secret-key");
            }
            
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
            
            sesClient = SesClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .build();
                    
            logger.info("SES Client initialized for region: {}", awsRegion);
        }
        return sesClient;
    }
    
    /**
     * Send family invitation email using SES
     */
    public void sendFamilyInvitationEmail(String toEmail, String familyName, String inviterName, String invitationToken) {
        try {
            logger.info("Sending family invitation email via SES to: {}", toEmail);
            
            String subject = "🏠 " + inviterName + " invited you to join " + familyName + " on FamilyNest!";
            String htmlContent = createFamilyInvitationHtml(toEmail, familyName, inviterName, invitationToken);
            
            sendEmail(toEmail, subject, htmlContent);
            
            logger.info("Family invitation email sent successfully via SES to: {}", toEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send family invitation email via SES to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send family invitation email via SES", e);
        }
    }
    
    /**
     * Send password reset email using SES
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            logger.info("Sending password reset email via SES to: {}", toEmail);
            
            String subject = "FamilyNest - Password Reset Request";
            String htmlContent = createPasswordResetHtml(toEmail, resetToken);
            
            sendEmail(toEmail, subject, htmlContent);
            
            logger.info("Password reset email sent successfully via SES to: {}", toEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email via SES to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email via SES", e);
        }
    }
    
    /**
     * Generic method to send email via SES
     */
    private void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            SesClient client = getSesClient();
            
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(htmlContent)
                                            .build())
                                    .build())
                            .build())
                    .source(fromEmail)
                    .build();
            
            SendEmailResponse response = client.sendEmail(emailRequest);
            logger.debug("SES Email sent. Message ID: {}", response.messageId());
            
        } catch (SesException e) {
            logger.error("SES Error: {}", e.getMessage());
            throw new RuntimeException("SES email sending failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create HTML content for family invitation email (reuse existing template)
     */
    private String createFamilyInvitationHtml(String inviteeEmail, String familyName, String inviterName, String invitationToken) {
        // Reuse the existing HTML template from EmailService
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
            .append(".content { padding: 40px 30px; }")
            .append(".welcome { text-align: center; margin-bottom: 40px; }")
            .append(".family-name { color: #667eea; font-weight: bold; }")
            .append("</style>")
            .append("</head><body>")
            .append("<div class='container'>");
        
        // Header
        html.append("<div class='header'>")
            .append("<h1>🏠 Welcome to FamilyNest</h1>")
            .append("<p>Private, secure family communication</p>")
            .append("</div>");
        
        // Content
        html.append("<div class='content'>")
            .append("<div class='welcome'>")
            .append("<h2>").append(inviterName).append(" invited you to join <span class='family-name'>").append(familyName).append("</span>!</h2>")
            .append("<p>Download the FamilyNest app and sign up with <strong>").append(inviteeEmail).append("</strong> to automatically see your invitation.</p>")
            .append("</div>")
            .append("</div>")
            .append("</div>")
            .append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * Create HTML content for password reset email
     */
    private String createPasswordResetHtml(String toEmail, String resetToken) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>")
            .append("<html><head>")
            .append("<meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }")
            .append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }")
            .append(".button { display: inline-block; padding: 12px 24px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; }")
            .append("</style>")
            .append("</head><body>")
            .append("<div class='container'>");
        
        html.append("<h2>FamilyNest - Password Reset</h2>")
            .append("<p>Hello,</p>")
            .append("<p>You have requested to reset your password for your FamilyNest account.</p>")
            .append("<p>To reset your password:</p>")
            .append("<p>1. Open the FamilyNest app<br>")
            .append("2. Go to 'Forgot Password'<br>") 
            .append("3. Enter this reset code: <strong>").append(resetToken).append("</strong></p>")
            .append("<p>This link will expire in 24 hours.</p>")
            .append("<p>If you did not request this password reset, please ignore this email.</p>")
            .append("<p>Best regards,<br>The FamilyNest Team</p>")
            .append("</div>")
            .append("</body></html>");
        
        return html.toString();
    }
}

