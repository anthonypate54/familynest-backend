package com.familynest.controller;

import com.familynest.model.User;
import com.familynest.repository.UserRepository;
import com.familynest.auth.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users/password-reset")
public class PasswordResetController {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AuthUtil authUtil;
    
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@RequestBody Map<String, String> requestData) {
        logger.debug("Received password reset request for email: {}", requestData.get("email"));
        try {
            String email = requestData.get("email");
            
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            
            // Find user by email (case-insensitive)
            Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());
            
            if (userOpt.isEmpty()) {
                // Don't reveal if email exists or not for security
                logger.debug("Password reset requested for non-existent email: {}", email);
                return ResponseEntity.ok(Map.of("message", "If the email exists, a password reset link will be sent"));
            }
            
            User user = userOpt.get();
            
            // Check if user already has a recent password reset request (prevent spam)
            if (user.getPasswordResetRequestedAt() != null && 
                user.getPasswordResetRequestedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                logger.debug("Password reset request too frequent for user: {}", user.getId());
                return ResponseEntity.ok(Map.of("message", "Please wait a few minutes before requesting another password reset"));
            }
            
            // Generate secure reset token
            String resetToken = java.util.UUID.randomUUID().toString();
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(1); // Token expires in 1 hour
            
            // Save token to user
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiresAt(expiresAt);
            user.setPasswordResetRequestedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Send password reset email (for now, just log the token)
            logger.info("Password reset token for user {}: {}", user.getId(), resetToken);
            logger.info("Password reset link: http://localhost:8080/password-reset?token={}", resetToken);
            
            // TODO: Replace with actual email service
            // emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
            
            logger.debug("Password reset request processed for user: {}", user.getId());
            return ResponseEntity.ok(Map.of("message", "If the email exists, a password reset link will be sent"));
        } catch (Exception e) {
            logger.error("Error processing password reset request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error processing password reset request"));
        }
    }
    
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(@RequestBody Map<String, String> resetData) {
        logger.debug("Received password reset confirmation");
        try {
            String token = resetData.get("token");
            String email = resetData.get("email");
            String newPassword = resetData.get("newPassword");
            
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reset token is required"));
            }
            
            // Check for null password
            if (newPassword == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
            }
            
            // We'll skip the length < 6 check since the validatePasswordStrength will catch it with the stricter requirement
            
            // Enhanced password strength validation
            Map<String, Object> passwordValidation = authUtil.validatePasswordStrength(newPassword);
            if (!(Boolean)passwordValidation.get("valid")) {
                logger.debug("Password strength validation failed: {}", passwordValidation.get("message"));
                return ResponseEntity.badRequest().body(Map.of("error", (String)passwordValidation.get("message")));
            }
            
            Optional<User> userOpt;
            
            // If email is provided, use it to find the user first (more efficient)
            if (email != null && !email.trim().isEmpty()) {
                userOpt = userRepository.findByEmail(email.toLowerCase());
                
                // Verify the token matches
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (user.getPasswordResetToken() == null || 
                        !user.getPasswordResetToken().equals(token) ||
                        user.getPasswordResetTokenExpiresAt() == null ||
                        user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
                        
                        logger.debug("Invalid or expired reset token for email: {}", email);
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
                    }
                } else {
                    logger.debug("User not found for email: {}", email);
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid email or reset token"));
                }
            } else {
                // Fall back to finding by token only
                userOpt = userRepository.findByValidPasswordResetToken(token);
                
                if (userOpt.isEmpty()) {
                    logger.debug("Invalid or expired reset token: {}", token);
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
                }
            }
            
            User user = userOpt.get();
            
            // Update password
            user.setPassword(authUtil.hashPassword(newPassword));
            
            // Clear reset token
            user.clearPasswordResetToken();
            
            // Save user
            userRepository.save(user);
            
            logger.debug("Password reset successful for user: {}", user.getId());
            return ResponseEntity.ok(Map.of("message", "Password reset successful. You can now login with your new password."));
        } catch (Exception e) {
            logger.error("Error confirming password reset: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error confirming password reset"));
        }
    }
    
    // Helper method to clean up expired password reset tokens (can be called by a scheduled task)
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredTokens() {
        logger.debug("Cleaning up expired password reset tokens");
        try {
            List<User> usersWithExpiredTokens = userRepository.findUsersWithExpiredPasswordResetTokens();
            
            for (User user : usersWithExpiredTokens) {
                user.clearPasswordResetToken();
                userRepository.save(user);
            }
            
            logger.debug("Cleaned up {} expired password reset tokens", usersWithExpiredTokens.size());
            return ResponseEntity.ok(Map.of("message", "Cleaned up " + usersWithExpiredTokens.size() + " expired tokens"));
        } catch (Exception e) {
            logger.error("Error cleaning up expired tokens: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error cleaning up expired tokens"));
        }
    }
} 