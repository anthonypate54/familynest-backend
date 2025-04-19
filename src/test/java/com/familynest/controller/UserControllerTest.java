package com.familynest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familynest.model.User;
import com.familynest.model.Family;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.MessageRepository;
import com.familynest.config.TestConfig;
import com.familynest.config.TestAuthFilter;
import com.familynest.auth.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
@TestPropertySource(locations = "classpath:application.properties")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FamilyRepository familyRepository;

    @MockBean
    private MessageRepository messageRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private User testUser;
    private Family testFamily;

    @BeforeEach
    public void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setRole("ADMIN");
        testUser.setFamilyId(null);

        testFamily = new Family();
        testFamily.setId(2L);
        testFamily.setName("Test Family");
    }

    @Test
    public void testCreateFamily_Success() throws Exception {
        // Setup user without existing family and with ADMIN role
        testUser.setRole("ADMIN");
        testUser.setFamilyId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        
        // Setup family to be created
        Family newFamily = new Family();
        newFamily.setId(2L);
        newFamily.setName("Test Family");
        when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            family.setId(2L);
            return family;
        });
        
        // Setup updated user with new family
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setUsername("testuser");
        updatedUser.setPassword("password123");
        updatedUser.setFirstName("Test");
        updatedUser.setLastName("User");
        updatedUser.setRole("ADMIN");
        updatedUser.setFamilyId(2L);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Generate a valid token for the test
        String token = jwtUtil.generateToken(1L, "ADMIN");

        Map<String, String> familyData = new HashMap<>();
        familyData.put("name", "Test Family");

        mockMvc.perform(post("/api/users/1/create-family")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(familyData))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.familyId").value(2));
    }
} 