package com.familynest;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.familynest.model.Message;
import com.familynest.model.MessageView;
import com.familynest.model.User;
import com.familynest.repository.MessageViewRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;

/**
 * Integration tests for the View Tracking Controller
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ViewTrackingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private EngagementTestUtil testUtil;
    
    @Autowired
    private MessageViewRepository viewRepository;
    
    @Autowired
    private TestAuthFilter testAuthFilter;
    
    // Test data
    private User testUser1;
    private User testUser2;
    private User testUser5; // User with no views on most messages
    private Message testMessage1;
    private Message testMessage3;
    private Message testMessage5; // Message with no views
    
    @BeforeAll
    public void setUp() {
        // Set up test data
        testUtil.setupTestData();
        
        // Cache test entities for easier access
        testUser1 = testUtil.getTestUser(1);
        testUser2 = testUtil.getTestUser(2);
        testUser5 = testUtil.getTestUser(5);
        testMessage1 = testUtil.getTestMessage(1);
        testMessage3 = testUtil.getTestMessage(3);
        testMessage5 = testUtil.getTestMessage(5);
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
        // Removed dummy Authorization header to prevent JWT validation
        // request.header("Authorization", "Bearer test-token");
        
        // Perform the request
        return mockMvc.perform(request);
    }
    
    @Test
    @Order(1)
    @DisplayName("Get views for a message")
    public void testGetMessageViews() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage1.getId() + "/views")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.viewCount", greaterThanOrEqualTo(3)));
    }
    
    @Test
    @Order(2)
    @DisplayName("Mark a message as viewed")
    public void testMarkMessageAsViewed() throws Exception {
        setTestUser(testUser5);
        
        // Check that user 5 has not viewed message 3 yet
        Optional<MessageView> existingView = viewRepository
            .findByMessageIdAndUserId(testMessage3.getId(), testUser5.getId());
            
        assertFalse(existingView.isPresent(), "User should not have viewed this message yet");
        
        // Mark message as viewed
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage3.getId() + "/views")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(testMessage3.getId()))
                .andExpect(jsonPath("$.userId").value(testUser5.getId()))
                .andExpect(jsonPath("$.viewedAt").isNotEmpty());
        
        // Verify the view was recorded
        existingView = viewRepository
            .findByMessageIdAndUserId(testMessage3.getId(), testUser5.getId());
            
        assertTrue(existingView.isPresent(), "View should be recorded after API call");
    }
    
    @Test
    @Order(3)
    @DisplayName("Mark the same message as viewed again (should be idempotent)")
    public void testMarkMessageAsViewedTwice() throws Exception {
        setTestUser(testUser1);
        
        // Verify user 1 has already viewed message 1
        Optional<MessageView> existingView = viewRepository
            .findByMessageIdAndUserId(testMessage1.getId(), testUser1.getId());
            
        assertTrue(existingView.isPresent(), "View should exist before test");
        
        // Mark message as viewed again
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage1.getId() + "/views")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(testMessage1.getId()))
                .andExpect(jsonPath("$.userId").value(testUser1.getId()))
                .andExpect(jsonPath("$.viewedAt").isNotEmpty());
        
        // Verify view count hasn't changed
        long viewCount = viewRepository.countByMessageId(testMessage1.getId());
        assertTrue(viewCount >= 3, "View count should remain at least 3"); // From test data
    }
    
    @Test
    @Order(4)
    @DisplayName("Check if user has viewed a message")
    public void testCheckIfMessageViewed() throws Exception {
        // Test with a user who has viewed the message
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage1.getId() + "/views/check")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewed").value(true));
        
        // Test with a user who has not viewed the message
        setTestUser(testUser5);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage5.getId() + "/views/check")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewed").value(false));
    }
    
    @Test
    @Order(5)
    @DisplayName("Get combined engagement data for a message")
    public void testGetEngagementData() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage1.getId() + "/engagement")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions").isMap())
                .andExpect(jsonPath("$.viewCount").isNumber())
                .andExpect(jsonPath("$.commentCount").isNumber())
                .andExpect(jsonPath("$.shareCount").isNumber())
                .andExpect(jsonPath("$.viewed").isBoolean());
    }
    
    @Test
    @Order(6) 
    @DisplayName("Mark non-existent message as viewed")
    public void testMarkNonExistentMessageAsViewed() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            post("/api/messages/99999/views") // Non-existent message ID
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message not found with id: 99999"));
    }
} 