package com.familynest.controller;

import com.familynest.model.User;
import com.familynest.repository.UserRepository;
import com.familynest.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {

    @Autowired
    private WebApplicationContext context;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private UserService userService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
                
        // Set up mock user
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");
        mockUser.setFirstName("Test");
        mockUser.setLastName("User");
        mockUser.setRole("USER");
        
        // Configure mocks
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userService.getUserById(1L)).thenReturn(mockUser);
        
        // Mock password validation
        Map<String, Object> validPasswordResult = new HashMap<>();
        validPasswordResult.put("valid", true);
        when(userService.validatePasswordStrength(eq("StrongPass123"))).thenReturn(validPasswordResult);
        
        Map<String, Object> invalidPasswordResult = new HashMap<>();
        invalidPasswordResult.put("valid", false);
        invalidPasswordResult.put("message", "Password must be at least 8 characters long");
        when(userService.validatePasswordStrength(eq("weak"))).thenReturn(invalidPasswordResult);
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testGetUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
    
    @Test
    public void testGetUserProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testUpdateUserProfile() throws Exception {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("firstName", "Updated");
        updateData.put("lastName", "Name");
        
        mockMvc.perform(post("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testChangePassword_Success() throws Exception {
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("currentPassword", "OldPass123");
        passwordData.put("newPassword", "StrongPass123");
        
        mockMvc.perform(post("/api/users/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testChangePassword_WeakPassword() throws Exception {
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("currentPassword", "OldPass123");
        passwordData.put("newPassword", "weak");
        
        mockMvc.perform(post("/api/users/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordData)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_TOO_WEAK"));
    }
}