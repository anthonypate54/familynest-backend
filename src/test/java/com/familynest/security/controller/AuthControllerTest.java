package com.familynest.security.controller;

import com.familynest.model.User;
import com.familynest.security.exception.SecurityException;
import com.familynest.security.jwt.TokenPair;
import com.familynest.security.service.AuthenticationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the AuthController with the new security system
 */
@WebMvcTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AuthenticationService authService;
    
    private TokenPair testTokenPair;
    private User testUser;
    
    @BeforeEach
    public void setup() {
        // Set up test token pair
        testTokenPair = new TokenPair(
                "test.access.token",
                "test.refresh.token",
                3600L,
                86400L
        );
        
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
        
        // Set up authentication service mock
        when(authService.authenticate(eq("testuser"), eq("password123")))
                .thenReturn(testTokenPair);
        when(authService.getUserByUsernameOrEmail(eq("testuser")))
                .thenReturn(testUser);
        
        // Set up registration mock
        when(authService.registerUser(
                eq("newuser"),
                eq("new@example.com"),
                eq("Password123"),
                eq("New"),
                eq("User")
        )).thenReturn(testUser);
        when(authService.generateTokenPair(any(User.class)))
                .thenReturn(testTokenPair);
        
        // Set up refresh token mock
        when(authService.refreshTokens(eq("test.refresh.token")))
                .thenReturn(testTokenPair);
        
        // Set up token validation mock
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("valid", true);
        tokenInfo.put("userId", 1L);
        tokenInfo.put("roles", "USER");
        when(authService.validateToken(eq("test.access.token")))
                .thenReturn(tokenInfo);
    }
    
    @Test
    public void testLogin_Success() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("usernameOrEmail", "testuser");
        credentials.put("password", "password123");
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("test.refresh.token"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(86400))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.photo").value("https://example.com/photo.jpg"));
    }
    
    @Test
    public void testLogin_InvalidCredentials() throws Exception {
        // Set up authentication service to throw exception for invalid credentials
        doThrow(new SecurityException(
                "Invalid username/email or password",
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS"
        )).when(authService).authenticate(eq("wronguser"), eq("wrongpass"));
        
        Map<String, String> credentials = new HashMap<>();
        credentials.put("usernameOrEmail", "wronguser");
        credentials.put("password", "wrongpass");
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username/email or password"));
    }
    
    @Test
    public void testRegister_Success() throws Exception {
        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("username", "newuser");
        registrationData.put("email", "new@example.com");
        registrationData.put("password", "Password123");
        registrationData.put("firstName", "New");
        registrationData.put("lastName", "User");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationData))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("test.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("test.refresh.token"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(86400))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
    
    @Test
    public void testRegister_UsernameTaken() throws Exception {
        // Set up registration service to throw exception for taken username
        doThrow(new SecurityException(
                "Username is already taken",
                HttpStatus.CONFLICT,
                "USERNAME_TAKEN"
        )).when(authService).registerUser(
                eq("takenuser"),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        
        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("username", "takenuser");
        registrationData.put("email", "new@example.com");
        registrationData.put("password", "Password123");
        registrationData.put("firstName", "New");
        registrationData.put("lastName", "User");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationData))
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }
    
    @Test
    public void testRefreshToken_Success() throws Exception {
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("refreshToken", "test.refresh.token");
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("test.refresh.token"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(86400));
    }
    
    @Test
    public void testRefreshToken_InvalidToken() throws Exception {
        // Set up refresh service to throw exception for invalid token
        doThrow(new SecurityException(
                "Invalid refresh token",
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN"
        )).when(authService).refreshTokens(eq("invalid.token"));
        
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("refreshToken", "invalid.token");
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }
    
    @Test
    public void testLogout_Success() throws Exception {
        Map<String, String> logoutRequest = new HashMap<>();
        logoutRequest.put("refreshToken", "test.refresh.token");
        
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
    
    @Test
    public void testValidateToken_Success() throws Exception {
        Map<String, String> validateRequest = new HashMap<>();
        validateRequest.put("token", "test.access.token");
        
        mockMvc.perform(post("/api/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validateRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.roles").value("USER"));
    }
}
