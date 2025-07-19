package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
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
} 