package com.familynest.security.controller;

import com.familynest.security.exception.SecurityException;
import com.familynest.security.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the PasswordResetController with the new security system
 */
@WebMvcTest(PasswordResetController.class)
public class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    @BeforeEach
    public void setup() {
        // Set up password reset token generation
        when(userService.generatePasswordResetToken(eq("test@example.com")))
                .thenReturn("test-reset-token");
        
        // Set up password reset
        when(userService.resetPassword(eq("test-reset-token"), eq("StrongPass123")))
                .thenReturn(true);
        when(userService.resetPassword(eq("invalid-token"), anyString()))
                .thenReturn(false);
        
        // Set up password validation
        Map<String, Object> validPasswordResult = new HashMap<>();
        validPasswordResult.put("valid", true);
        when(userService.validatePasswordStrength(eq("StrongPass123")))
                .thenReturn(validPasswordResult);
        
        Map<String, Object> weakPasswordResult = new HashMap<>();
        weakPasswordResult.put("valid", false);
        weakPasswordResult.put("message", "Password must be at least 8 characters long");
        when(userService.validatePasswordStrength(eq("weak")))
                .thenReturn(weakPasswordResult);
    }
    
    @Test
    public void testRequestPasswordReset_Success() throws Exception {
        Map<String, String> resetRequest = new HashMap<>();
        resetRequest.put("email", "test@example.com");
        
        mockMvc.perform(post("/api/password/reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset link has been sent to your email"))
                .andExpect(jsonPath("$.resetToken").value("test-reset-token"));
    }
    
    @Test
    public void testRequestPasswordReset_UserNotFound() throws Exception {
        // Set up user service to throw exception for non-existent email
        doThrow(new SecurityException(
                "No user found with email: nonexistent@example.com",
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND"
        )).when(userService).generatePasswordResetToken(eq("nonexistent@example.com"));
        
        Map<String, String> resetRequest = new HashMap<>();
        resetRequest.put("email", "nonexistent@example.com");
        
        // Even though the user doesn't exist, we should return a generic success message
        // to prevent user enumeration attacks
        mockMvc.perform(post("/api/password/reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If your email is registered, a password reset link will be sent"));
    }
    
    @Test
    public void testResetPassword_Success() throws Exception {
        Map<String, String> resetData = new HashMap<>();
        resetData.put("resetToken", "test-reset-token");
        resetData.put("newPassword", "StrongPass123");
        
        mockMvc.perform(post("/api/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetData))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));
    }
    
    @Test
    public void testResetPassword_InvalidToken() throws Exception {
        Map<String, String> resetData = new HashMap<>();
        resetData.put("resetToken", "invalid-token");
        resetData.put("newPassword", "StrongPass123");
        
        mockMvc.perform(post("/api/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetData))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }
    
    @Test
    public void testResetPassword_WeakPassword() throws Exception {
        Map<String, String> resetData = new HashMap<>();
        resetData.put("resetToken", "test-reset-token");
        resetData.put("newPassword", "weak");
        
        mockMvc.perform(post("/api/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetData))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_TOO_WEAK"))
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters long"));
    }
}





