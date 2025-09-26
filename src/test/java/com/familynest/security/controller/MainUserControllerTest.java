package com.familynest.security.controller;

import com.familynest.model.User;
import com.familynest.model.UserPreferences;
import com.familynest.repository.UserPreferencesRepository;
import com.familynest.repository.UserRepository;
import com.familynest.security.exception.SecurityException;
import com.familynest.security.service.UserService;
import com.familynest.service.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the MainUserController with the new security system
 */
@WebMvcTest(MainUserController.class)
public class MainUserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private UserPreferencesRepository userPreferencesRepository;
    
    @MockBean
    private StorageService storageService;
    
    private User testUser;
    private UserPreferences testPreferences;
    
    @BeforeEach
    public void setup() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setPassword("$2a$10$abcdefghijklmnopqrstuvwxyz"); // Hashed password
        testUser.setRole("USER");
        testUser.setPhoto("https://example.com/photo.jpg");
        
        // Set up test preferences
        testPreferences = new UserPreferences();
        testPreferences.setUserId(1L);
        testPreferences.setShowAddress(true);
        testPreferences.setShowPhoneNumber(true);
        testPreferences.setShowBirthday(true);
        testPreferences.setFamilyMessagesNotifications(true);
        testPreferences.setNewMemberNotifications(true);
        
        // Mock userService
        when(userService.getUserById(1L)).thenReturn(testUser);
        
        // Mock userRepository
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // Mock userPreferencesRepository
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.of(testPreferences));
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenReturn(testPreferences);
        
        // Mock storageService
        when(storageService.store(any(), any(), any())).thenReturn("https://example.com/new-photo.jpg");
        
        // Mock password validation
        Map<String, Object> validPasswordResult = new HashMap<>();
        validPasswordResult.put("valid", true);
        when(userService.validatePasswordStrength(eq("StrongPass123"))).thenReturn(validPasswordResult);
        
        Map<String, Object> weakPasswordResult = new HashMap<>();
        weakPasswordResult.put("valid", false);
        weakPasswordResult.put("message", "Password must be at least 8 characters long");
        when(userService.validatePasswordStrength(eq("weak"))).thenReturn(weakPasswordResult);
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testGetUserProfile_Success() throws Exception {
        mockMvc.perform(get("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.photo").value("https://example.com/photo.jpg"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
    
    @Test
    public void testGetUserProfile_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testGetUserProfile_UserNotFound() throws Exception {
        // Mock userService to throw exception
        doThrow(new SecurityException(
                "User not found with ID: 1",
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND"
        )).when(userService).getUserById(1L);
        
        mockMvc.perform(get("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with ID: 1"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testUpdateProfile_Success() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", "Updated");
        updates.put("lastName", "Name");
        
        mockMvc.perform(post("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testUpdateProfile_EmailAlreadyExists() throws Exception {
        // Set up a user with the email we're trying to change to
        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setEmail("existing@example.com");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", "existing@example.com");
        
        mockMvc.perform(post("/api/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testChangePassword_Success() throws Exception {
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("currentPassword", "OldPass123");
        passwordData.put("newPassword", "StrongPass123");
        
        mockMvc.perform(post("/api/users/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordData))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testChangePassword_WeakPassword() throws Exception {
        // Set up userService to throw exception for weak password
        doThrow(new SecurityException(
                "Password must be at least 8 characters long",
                HttpStatus.BAD_REQUEST,
                "PASSWORD_TOO_WEAK"
        )).when(userService).changePassword(eq(1L), eq("OldPass123"), eq("weak"));
        
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("currentPassword", "OldPass123");
        passwordData.put("newPassword", "weak");
        
        mockMvc.perform(post("/api/users/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordData))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_TOO_WEAK"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testChangePassword_IncorrectCurrentPassword() throws Exception {
        // Set up userService to throw exception for incorrect current password
        doThrow(new SecurityException(
                "Current password is incorrect",
                HttpStatus.BAD_REQUEST,
                "INVALID_PASSWORD"
        )).when(userService).changePassword(eq(1L), eq("WrongPass"), eq("StrongPass123"));
        
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("currentPassword", "WrongPass");
        passwordData.put("newPassword", "StrongPass123");
        
        mockMvc.perform(post("/api/users/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordData))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testUploadProfilePhoto_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes());
        
        mockMvc.perform(multipart("/api/users/profile-photo")
                .file(file)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile photo uploaded successfully"))
                .andExpect(jsonPath("$.photoUrl").value("https://example.com/new-photo.jpg"));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testGetUserPreferences_Existing() throws Exception {
        mockMvc.perform(get("/api/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showAddress").value(true))
                .andExpect(jsonPath("$.showPhoneNumber").value(true))
                .andExpect(jsonPath("$.showBirthday").value(true))
                .andExpect(jsonPath("$.familyMessagesNotifications").value(true))
                .andExpect(jsonPath("$.newMemberNotifications").value(true));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testGetUserPreferences_CreateNew() throws Exception {
        // Mock empty preferences
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        
        // Mock saving new preferences
        UserPreferences newPrefs = new UserPreferences();
        newPrefs.setUserId(1L);
        newPrefs.setShowAddress(true);
        newPrefs.setShowPhoneNumber(true);
        newPrefs.setShowBirthday(true);
        newPrefs.setFamilyMessagesNotifications(true);
        newPrefs.setNewMemberNotifications(true);
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenReturn(newPrefs);
        
        mockMvc.perform(get("/api/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showAddress").value(true))
                .andExpect(jsonPath("$.showPhoneNumber").value(true))
                .andExpect(jsonPath("$.showBirthday").value(true))
                .andExpect(jsonPath("$.familyMessagesNotifications").value(true))
                .andExpect(jsonPath("$.newMemberNotifications").value(true));
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    public void testUpdateUserPreferences_Success() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("showAddress", false);
        updates.put("showPhoneNumber", false);
        
        mockMvc.perform(post("/api/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showAddress").value(true)) // Mock returns original values
                .andExpect(jsonPath("$.showPhoneNumber").value(true)); // Mock returns original values
    }
}
