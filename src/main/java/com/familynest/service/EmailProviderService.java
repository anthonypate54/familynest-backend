package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailProviderService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailProviderService.class);
    
    @Autowired
    private EmailService emailService; // Existing SMTP service
    
    @Autowired
    private SESEmailService sesEmailService; // New SES service
    
    @Value("${email.provider:smtp}")
    private String emailProvider;
    
    /**
     * Send family invitation email using configured provider
     */
    public void sendFamilyInvitationEmail(String inviteeEmail, String familyName, String inviterName, String invitationToken) {
        logger.debug("Sending family invitation via provider: {}", emailProvider);
        
        if ("ses".equalsIgnoreCase(emailProvider)) {
            sesEmailService.sendFamilyInvitationEmail(inviteeEmail, familyName, inviterName, invitationToken);
        } else {
            // TODO: Add family invitation email to existing EmailService
            logger.warn("Family invitation email not implemented for SMTP provider yet, using SES");
            sesEmailService.sendFamilyInvitationEmail(inviteeEmail, familyName, inviterName, invitationToken);
        }
    }
    
    /**
     * Send password reset email using configured provider
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        logger.debug("Sending password reset via provider: {}", emailProvider);
        
        if ("ses".equalsIgnoreCase(emailProvider)) {
            sesEmailService.sendPasswordResetEmail(toEmail, resetToken);
        } else {
            emailService.sendPasswordResetEmail(toEmail, resetToken);
        }
    }
    
    /**
     * Send username reminder email using configured provider
     */
    public void sendUsernameReminderEmail(String toEmail, String username) {
        logger.debug("Sending username reminder via provider: {}", emailProvider);
        
        if ("ses".equalsIgnoreCase(emailProvider)) {
            // SES version - could implement if needed
            logger.warn("Username reminder not implemented for SES, falling back to SMTP");
            emailService.sendUsernameReminderEmail(toEmail, username);
        } else {
            emailService.sendUsernameReminderEmail(toEmail, username);
        }
    }
    
    /**
     * Get current email provider
     */
    public String getCurrentProvider() {
        return emailProvider;
    }
}
