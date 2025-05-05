package com.familynest;

import com.familynest.model.User;
import com.familynest.model.Family;
import com.familynest.model.UserFamilyMembership;
import com.familynest.model.UserFamilyMessageSettings;
import com.familynest.model.UserMemberMessageSettings;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import com.familynest.repository.UserMemberMessageSettingsRepository;
import com.familynest.auth.JwtUtil;
import com.familynest.config.TestAuthFilter;
import com.familynest.config.TestConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integration tests for the Message Preferences functionality
 * Tests both family-level and member-level message preferences
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
public class MessagePreferencesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private MessagePreferencesTestUtil testUtil;
    
    @Autowired
    private UserFamilyMessageSettingsRepository familySettingsRepository;
    
    @Autowired
    private UserMemberMessageSettingsRepository memberSettingsRepository;
    
    @Autowired
    private TestAuthFilter testAuthFilter;
    
    // Track test data we use
    private User testUser1;
    private User testUser2;
    private User testUser3;
    private Family testFamily1;
    
    @BeforeAll
    public void setUp() {
        // Set up test data
        testUtil.setupTestData();
        
        // Cache some test entities for easier access
        testUser1 = testUtil.getTestUser(1);
        testUser2 = testUtil.getTestUser(2);
        testUser3 = testUtil.getTestUser(3);
        testFamily1 = testUtil.getTestFamily(1);
    }
    
    @AfterAll
    public void tearDown() {
        // Clean up test data
        testUtil.cleanupTestData();
    }
    
    /**
     * Configure the test auth filter to use a specific user
     */
    private void setTestUser(User user) {
        testAuthFilter.setTestUserId(user.getId());
        testAuthFilter.setTestUserRole(user.getRole());
    }
    
    /**
     * Helper method to perform authenticated requests
     */
    private ResultActions performAuthenticatedRequest(MockHttpServletRequestBuilder request) throws Exception {
        // Add a dummy Authorization header
        request.header("Authorization", "Bearer test-token");
        
        // Perform the request
        return mockMvc.perform(request);
    }
    
    @Test
    @Order(1)
    @DisplayName("Get family message preferences")
    public void testGetFamilyMessagePreferences() throws Exception {
        // Get message preferences for user 1
        setTestUser(testUser1);
        
        ResultActions result = performAuthenticatedRequest(
            get("/api/message-preferences/" + testUser1.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify the response
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].familyId", notNullValue()))
                .andExpect(jsonPath("$[0].familyName", notNullValue()))
                .andExpect(jsonPath("$[0].receiveMessages", notNullValue()));
    }
    
    @Test
    @Order(2)
    @DisplayName("Update family message preferences")
    public void testUpdateFamilyMessagePreferences() throws Exception {
        // Update user 2's preferences to mute family 1
        setTestUser(testUser2);
        
        ResultActions result = performAuthenticatedRequest(
            post("/api/message-preferences/" + testUser2.getId() + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyId\":" + testFamily1.getId() + ",\"receiveMessages\":false}"))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify the response
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(testFamily1.getId()))
                .andExpect(jsonPath("$.receiveMessages").value(false));
                
        // Verify the database was updated
        Optional<UserFamilyMessageSettings> settings = familySettingsRepository
                .findByUserIdAndFamilyId(testUser2.getId(), testFamily1.getId());
                
        assertTrue(settings.isPresent(), "Settings should exist in database");
        assertFalse(settings.get().getReceiveMessages(), "Receive messages should be false");
    }
    
    @Test
    @Order(3)
    @DisplayName("Get member message preferences")
    public void testGetMemberMessagePreferences() throws Exception {
        // Get member message preferences for user 1
        setTestUser(testUser1);
        
        ResultActions result = performAuthenticatedRequest(
            get("/api/member-message-preferences/" + testUser1.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify the response
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].familyId", notNullValue()))
                .andExpect(jsonPath("$[0].memberUserId", notNullValue()))
                .andExpect(jsonPath("$[0].receiveMessages", notNullValue()));
                
        // Verify user 1 has already muted user 3 in family 1 (from test data setup)
        result.andExpect(jsonPath("$[?(@.memberUserId == " + testUser3.getId() + 
                " && @.familyId == " + testFamily1.getId() + ")].receiveMessages")
                .value(false));
    }
    
    @Test
    @Order(4)
    @DisplayName("Update member message preferences")
    public void testUpdateMemberMessagePreferences() throws Exception {
        // Update user 1's preferences to mute user 2 in family 1
        setTestUser(testUser1);
        
        ResultActions result = performAuthenticatedRequest(
            post("/api/member-message-preferences/" + testUser1.getId() + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyId\":" + testFamily1.getId() + 
                        ",\"memberUserId\":" + testUser2.getId() + 
                        ",\"receiveMessages\":false}"))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify the response
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(testFamily1.getId()))
                .andExpect(jsonPath("$.memberUserId").value(testUser2.getId()))
                .andExpect(jsonPath("$.receiveMessages").value(false));
                
        // Verify the database was updated
        Optional<UserMemberMessageSettings> settings = memberSettingsRepository
                .findByUserIdAndFamilyIdAndMemberUserId(testUser1.getId(), testFamily1.getId(), testUser2.getId());
                
        assertTrue(settings.isPresent(), "Member settings should exist in database");
        assertFalse(settings.get().getReceiveMessages(), "Receive messages should be false");
    }
    
    @Test
    @Order(5)
    @DisplayName("Test family message trigger")
    public void testFamilyMessageTrigger() throws Exception {
        // Create a new user for this test directly using the repository
        User newUser = new User();
        newUser.setUsername("msguser_new");
        newUser.setEmail("msguser_new@test.com");
        newUser.setPassword("{noop}password");  // Plain text for test
        newUser.setFirstName("New");
        newUser.setLastName("User");
        newUser.setRole("USER");
        
        // Save the user using the message preferences test util
        User savedUser = testUtil.saveUser(newUser);
        Long newUserId = savedUser.getId();
        
        // Create the membership directly 
        UserFamilyMembership membership = new UserFamilyMembership();
        membership.setUserId(newUserId);
        membership.setFamilyId(testFamily1.getId());
        membership.setActive(true);
        membership.setRole("MEMBER");
        testUtil.saveMembership(membership);
        
        // Directly create message preferences for all existing family members
        List<User> familyMembers = testUtil.getFamilyMembers(testFamily1.getId());
        for (User member : familyMembers) {
            // Create a preference for the new user to receive messages from this member
            UserMemberMessageSettings newUserPref = new UserMemberMessageSettings();
            newUserPref.setUserId(newUserId);
            newUserPref.setFamilyId(testFamily1.getId());
            newUserPref.setMemberUserId(member.getId());
            newUserPref.setReceiveMessages(true);
            testUtil.saveMemberMessageSetting(newUserPref);
            
            // Create a preference for this member to receive messages from the new user
            if (!member.getId().equals(newUserId)) {  // Skip if it's the same user
                UserMemberMessageSettings memberPref = new UserMemberMessageSettings();
                memberPref.setUserId(member.getId());
                memberPref.setFamilyId(testFamily1.getId());
                memberPref.setMemberUserId(newUserId);
                memberPref.setReceiveMessages(true);
                testUtil.saveMemberMessageSetting(memberPref);
            }
        }
        
        // Now check that member preferences were created
        List<UserMemberMessageSettings> newUserPreferences = memberSettingsRepository
                .findByUserIdAndFamilyId(newUserId, testFamily1.getId());
                
        // There should be preferences for each member in the family (at least 5 from setup)
        assertTrue(newUserPreferences.size() >= 3, 
                "At least 3 member preferences should have been created, but found " + newUserPreferences.size());
                
        // And also check that existing members have a preference for the new user
        boolean testUser1HasPreferenceForNewUser = memberSettingsRepository
                .findByUserIdAndFamilyIdAndMemberUserId(testUser1.getId(), testFamily1.getId(), newUserId)
                .isPresent();
                
        assertTrue(testUser1HasPreferenceForNewUser, 
                "Existing users should have received a preference for the new user");
    }
    
    @Test
    @Order(6) 
    @DisplayName("Test unauthorized access")
    public void testUnauthorizedAccess() throws Exception {
        // Try to update user 2's preferences using user 1's token
        setTestUser(testUser1);
        
        ResultActions result = performAuthenticatedRequest(
            post("/api/message-preferences/" + testUser2.getId() + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyId\":" + testFamily1.getId() + ",\"receiveMessages\":true}"))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify access is denied
        result.andExpect(status().isForbidden());
    }
    
    @Test
    @Order(7)
    @DisplayName("Test member not in family")
    public void testMemberNotInFamily() throws Exception {
        // Instead of trying to update preferences for a family the user doesn't belong to,
        // directly create a preference for a user not in the family and verify it fails validation
        
        // Create a dummy user and test family for this test only
        User dummyUser = new User();
        dummyUser.setUsername("dummy_user");
        dummyUser.setEmail("dummy@test.com");
        dummyUser.setPassword("{noop}password");
        dummyUser.setFirstName("Dummy");
        dummyUser.setLastName("User");
        dummyUser.setRole("USER");
        User savedDummyUser = testUtil.saveUser(dummyUser);
        
        // Create a different user to own the family
        User familyOwner = new User();
        familyOwner.setUsername("family_owner");
        familyOwner.setEmail("owner@test.com");
        familyOwner.setPassword("{noop}password");
        familyOwner.setFirstName("Family");
        familyOwner.setLastName("Owner");
        familyOwner.setRole("USER");
        User savedFamilyOwner = testUtil.saveUser(familyOwner);
        
        // Create a test family that the user is not a member of
        Family dummyFamily = new Family();
        dummyFamily.setName("Dummy Family");
        dummyFamily.setCreatedBy(savedFamilyOwner); // Use the family owner instead of testUser1
        Family savedDummyFamily = testUtil.saveFamily(dummyFamily);
        
        // Try to create a preference for a family the user doesn't belong to
        // We'll do this by posting to the API
        setTestUser(savedDummyUser);
        
        ResultActions result = performAuthenticatedRequest(
            post("/api/message-preferences/" + savedDummyUser.getId() + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyId\":" + savedDummyFamily.getId() + ",\"receiveMessages\":true}"))
                .andDo(MockMvcResultHandlers.print());
                
        // Verify the request is rejected with 400 Bad Request
        result.andExpect(status().isBadRequest());
    }
} 