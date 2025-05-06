package com.familynest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Test class to validate metrics endpoints for UI widget testing
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testdb")
public class MetricWidgetsTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private TestAuthProvider testAuthProvider;
    
    private static final Long TEST_USER_ID = 101L;
    private static final Long TEST_MESSAGE_ID = 1L;

    /**
     * Test getting engagement metrics for a message
     */
    @Test
    public void testGetMessageEngagementData() throws Exception {
        // Get a valid token for the test user
        String token = testAuthProvider.generateTestToken(TEST_USER_ID);
        
        // Get message engagement data for a sample message
        ResultActions result = mockMvc.perform(
                MockMvcRequestBuilders.get("/api/messages/" + TEST_MESSAGE_ID + "/engagement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
        );

        // Verify the response structure
        result.andExpect(MockMvcResultMatchers.status().isOk())
              .andExpect(MockMvcResultMatchers.jsonPath("$.reactions").exists())
              .andExpect(MockMvcResultMatchers.jsonPath("$.commentCount").exists())
              .andExpect(MockMvcResultMatchers.jsonPath("$.viewCount").exists())
              .andExpect(MockMvcResultMatchers.jsonPath("$.shareCount").exists());
        
        // Print the response for debugging
        System.out.println("Message Engagement Data for ID " + TEST_MESSAGE_ID + ": " + 
                           result.andReturn().getResponse().getContentAsString());
    }

    /**
     * Test getting view counts for a message
     */
    @Test
    public void testGetMessageViews() throws Exception {
        // Get a valid token for the test user
        String token = testAuthProvider.generateTestToken(TEST_USER_ID);
        
        // Get message views for a sample message
        ResultActions result = mockMvc.perform(
                MockMvcRequestBuilders.get("/api/messages/" + TEST_MESSAGE_ID + "/views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
        );

        // Verify the response structure
        result.andExpect(MockMvcResultMatchers.status().isOk())
              .andExpect(MockMvcResultMatchers.jsonPath("$.views").exists())
              .andExpect(MockMvcResultMatchers.jsonPath("$.viewCount").exists());
        
        // Print the response for debugging
        System.out.println("Message Views for ID " + TEST_MESSAGE_ID + ": " + 
                           result.andReturn().getResponse().getContentAsString());
    }

    /**
     * Test getting reactions for a message
     */
    @Test
    public void testGetMessageReactions() throws Exception {
        // Get a valid token for the test user
        String token = testAuthProvider.generateTestToken(TEST_USER_ID);
        
        // Get message reactions for a sample message
        ResultActions result = mockMvc.perform(
                MockMvcRequestBuilders.get("/api/messages/" + TEST_MESSAGE_ID + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
        );

        // Verify the response is successful
        result.andExpect(MockMvcResultMatchers.status().isOk());
        
        // Print the response for debugging
        System.out.println("Message Reactions for ID " + TEST_MESSAGE_ID + ": " + 
                           result.andReturn().getResponse().getContentAsString());
    }
    
    /**
     * Test getting comments for a message
     */
    @Test
    public void testGetMessageComments() throws Exception {
        // Get a valid token for the test user
        String token = testAuthProvider.generateTestToken(TEST_USER_ID);
        
        // Get message comments for a sample message
        ResultActions result = mockMvc.perform(
                MockMvcRequestBuilders.get("/api/messages/" + TEST_MESSAGE_ID + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
        );

        // Verify the response is successful
        result.andExpect(MockMvcResultMatchers.status().isOk());
        
        // Print the response for debugging
        System.out.println("Message Comments for ID " + TEST_MESSAGE_ID + ": " + 
                           result.andReturn().getResponse().getContentAsString());
    }
} 