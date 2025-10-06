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
    
    @Value("${email.testing.redirect:}")
    private String testingRedirectEmail;
    
    /**
     * Apply email redirect if testing mode is enabled
     * @param originalEmail The intended recipient email
     * @return The effective email address (redirected or original)
     */
    private String applyEmailRedirect(String originalEmail) {
        if (testingRedirectEmail != null && !testingRedirectEmail.trim().isEmpty()) {
            logger.info("📧 EMAIL REDIRECT: {} → {} (testing mode)", originalEmail, testingRedirectEmail);
            return testingRedirectEmail.trim();
        }
        return originalEmail;
    }
    
    /**
     * Send email using configured provider with automatic redirect handling
     */
    private void sendEmailWithProvider(String originalEmail, String emailType, 
                                     java.util.function.Consumer<String> sesAction, 
                                     java.util.function.Consumer<String> smtpAction) {
        logger.debug("Sending {} via provider: {}", emailType, emailProvider);
        
        String effectiveEmail = applyEmailRedirect(originalEmail);
        
        if ("ses".equalsIgnoreCase(emailProvider)) {
            sesAction.accept(effectiveEmail);
        } else {
            smtpAction.accept(effectiveEmail);
        }
    }
    
    /**
     * Send family invitation email using configured provider
     */
    public void sendFamilyInvitationEmail(String inviteeEmail, String familyName, String inviterName, String invitationToken) {
        sendEmailWithProvider(inviteeEmail, "family invitation",
            effectiveEmail -> sesEmailService.sendFamilyInvitationEmail(effectiveEmail, familyName, inviterName, invitationToken),
            effectiveEmail -> emailService.sendFamilyInvitationEmail(effectiveEmail, familyName, inviterName, invitationToken)
        );
    }
    
    /**
     * Send password reset email using configured provider
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        sendEmailWithProvider(toEmail, "password reset",
            effectiveEmail -> sesEmailService.sendPasswordResetEmail(effectiveEmail, resetToken),
            effectiveEmail -> emailService.sendPasswordResetEmail(effectiveEmail, resetToken)
        );
    }
    
    /**
     * Send username reminder email using configured provider
     */
    public void sendUsernameReminderEmail(String toEmail, String username) {
        sendEmailWithProvider(toEmail, "username reminder",
            effectiveEmail -> {
                // SES version - could implement if needed
                logger.warn("Username reminder not implemented for SES, falling back to SMTP");
                emailService.sendUsernameReminderEmail(effectiveEmail, username);
            },
            effectiveEmail -> emailService.sendUsernameReminderEmail(effectiveEmail, username)
        );
    }
    
    /**
     * Get current email provider
     */
    public String getCurrentProvider() {
        return emailProvider;
    }
}

