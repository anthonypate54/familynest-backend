package com.familynest;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.familynest.model.Message;
import com.familynest.model.MessageReaction;
import com.familynest.model.User;
import com.familynest.repository.MessageReactionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;

/**
 * Integration tests for the Reaction Controller
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ReactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private EngagementTestUtil testUtil;
    
    @Autowired
    private MessageReactionRepository reactionRepository;
    
    @Autowired
    private TestAuthFilter testAuthFilter;
    
    // Test data
    private User testUser1;
    private User testUser2;
    private Message testMessage1;
    private Message testMessage2;
    
    @BeforeAll
    public void setUp() {
        // Set up test data
        testUtil.setupTestData();
        
        // Cache test entities for easier access
        testUser1 = testUtil.getTestUser(1);
        testUser2 = testUtil.getTestUser(2);
        testMessage1 = testUtil.getTestMessage(1);
        testMessage2 = testUtil.getTestMessage(2);
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
        // Customize the TestAuthFilter to use a specific user ID
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
    @DisplayName("Get reactions for a message")
    public void testGetReactions() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage1.getId() + "/reactions")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.counts", notNullValue()))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(3)));
    }
    
    @Test
    @Order(2)
    @DisplayName("Add a new reaction to a message")
    public void testAddReaction() throws Exception {
        setTestUser(testUser1);
        
        // Count initial reactions of type "WOW" for test message 1
        long initialCount = reactionRepository.countByMessageIdAndReactionType(testMessage2.getId(), "WOW");
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "WOW");
        
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage2.getId() + "/reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(testMessage2.getId()))
                .andExpect(jsonPath("$.userId").value(testUser1.getId()))
                .andExpect(jsonPath("$.reactionType").value("WOW"));
        
        // Verify reaction was added to the database
        long newCount = reactionRepository.countByMessageIdAndReactionType(testMessage2.getId(), "WOW");
        assertEquals(initialCount + 1, newCount, "Reaction count should be incremented by 1");
    }
    
    @Test
    @Order(3)
    @DisplayName("Toggle off an existing reaction (remove)")
    public void testToggleOffReaction() throws Exception {
        setTestUser(testUser1);
        
        // First, verify the reaction exists
        Optional<MessageReaction> existingReaction = reactionRepository
            .findByMessageIdAndUserIdAndReactionType(testMessage1.getId(), testUser1.getId(), "LIKE");
        
        assertTrue(existingReaction.isPresent(), "Test reaction should exist before toggling");
        
        // Toggle it off by posting the same reaction again
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "LIKE");
        
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage1.getId() + "/reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reaction removed successfully"));
        
        // Verify the reaction was removed
        Optional<MessageReaction> removedReaction = reactionRepository
            .findByMessageIdAndUserIdAndReactionType(testMessage1.getId(), testUser1.getId(), "LIKE");
        
        assertFalse(removedReaction.isPresent(), "Reaction should be removed after toggling");
    }
    
    @Test
    @Order(4)
    @DisplayName("Remove a reaction using DELETE endpoint")
    public void testRemoveReaction() throws Exception {
        setTestUser(testUser2);
        
        // First add a reaction to delete
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "SAD");
        
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage2.getId() + "/reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isCreated());
        
        // Now delete it
        performAuthenticatedRequest(
            delete("/api/messages/" + testMessage2.getId() + "/reactions/SAD")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reaction removed successfully"));
        
        // Verify it was removed
        Optional<MessageReaction> removedReaction = reactionRepository
            .findByMessageIdAndUserIdAndReactionType(testMessage2.getId(), testUser2.getId(), "SAD");
        
        assertFalse(removedReaction.isPresent(), "Reaction should be removed");
    }
    
    @Test
    @Order(5)
    @DisplayName("Try to add invalid reaction type")
    public void testAddInvalidReaction() throws Exception {
        setTestUser(testUser1);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", ""); // Empty reaction type
        
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage1.getId() + "/reactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Reaction type is required"));
    }
    
    @Test
    @Order(6)
    @DisplayName("Try to add reaction to non-existent message")
    public void testAddReactionToNonExistentMessage() throws Exception {
        setTestUser(testUser1);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "LIKE");
        
        performAuthenticatedRequest(
            post("/api/messages/99999/reactions") // Non-existent message ID
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message not found with id: 99999"));
    }
} 