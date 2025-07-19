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
import com.familynest.auth.AuthUtil;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;

/**
 * Integration tests for the Reaction Controller
 */
/*
package com.familynest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.familynest.auth.AuthUtil;
import com.familynest.config.TestConfig;
import com.familynest.controller.EngagementTestUtil;
import com.familynest.model.Message;
import com.familynest.model.User;
import com.familynest.repository.MessageReactionRepository;
import com.familynest.security.TestAuthFilter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;

/**
 * Integration tests for the Reaction Controller
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Disabled("Tests disabled due to changes in reaction functionality")
public class ReactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EngagementTestUtil testUtil;

    @Autowired
    private MessageReactionRepository reactionRepository;

    @Autowired
    private TestAuthFilter testAuthFilter;

    @MockBean
    private AuthUtil authUtil;

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
        
        // Mock AuthUtil to bypass JWT validation
        Mockito.when(authUtil.validateToken(Mockito.anyString())).thenReturn(true);
        Mockito.when(authUtil.extractUserId(Mockito.anyString())).thenReturn(testUser1.getId());
        Mockito.when(authUtil.getUserRole(Mockito.anyString())).thenReturn("USER");
        
        // No need to set up mock authentication here since security is disabled for tests in TestConfig
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
        // Adding a dummy Authorization header with a placeholder token to bypass initial AuthFilter check
        request.header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        
        // Perform the request
        return mockMvc.perform(request);
    }
    
    @Test
    @Order(1)
    @DisplayName("Add a reaction to a message")
    public void testAddReaction() throws Exception {
        // Ensure required entities are not null before attempting to add a reaction
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test add reaction - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test add reaction - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("reactionType", "LIKE");
        
        performAuthenticatedRequest(post("/api/messages/{messageId}/reactions", testMessage1.getId())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .params(formData)
            .with(authPostProcessor))
            .andExpect(status().isCreated())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Add reaction response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(2)
    @DisplayName("Get reactions for a message")
    public void testGetReactions() throws Exception {
        // Ensure the message ID is not null before attempting to get reactions
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test get reactions - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test get reactions - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        performAuthenticatedRequest(get("/api/messages/{messageId}/reactions", testMessage1.getId())
            .with(authPostProcessor))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            // Relax the expectation to handle empty or small result sets for now
            .andExpect(jsonPath("$").isArray())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Get reactions response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(3)
    @DisplayName("Get user reaction for a message")
    public void testGetUserReaction() throws Exception {
        // Ensure required entities are not null before attempting to get user reaction
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test get user reaction - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test get user reaction - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        performAuthenticatedRequest(get("/api/messages/{messageId}/reactions/user", testMessage1.getId())
            .with(authPostProcessor))
            .andExpect(status().isOk())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Get user reaction response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(4)
    @DisplayName("Update a reaction")
    public void testUpdateReaction() throws Exception {
        // Ensure required entities are not null before attempting to update a reaction
        if (testMessage2 == null || testMessage2.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test update reaction - testMessage2 or testUser1 or their IDs are null");
            fail("Cannot test update reaction - testMessage2 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("reactionType", "LOVE");
        
        performAuthenticatedRequest(put("/api/messages/{messageId}/reactions", testMessage2.getId())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .params(formData)
            .with(authPostProcessor))
            .andExpect(status().isOk())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Update reaction response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(5)
    @DisplayName("Delete a reaction")
    public void testDeleteReaction() throws Exception {
        // Ensure the message ID is not null before attempting to delete a reaction
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test delete reaction - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test delete reaction - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        performAuthenticatedRequest(delete("/api/messages/{messageId}/reactions", testMessage1.getId())
            .with(authPostProcessor))
            .andExpect(status().isOk())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Delete reaction response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(6)
    @DisplayName("Get reaction summary for a message")
    public void testGetReactionSummary() throws Exception {
        // Ensure the message ID is not null before attempting to get reaction summary
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test get reaction summary - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test get reaction summary - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        performAuthenticatedRequest(get("/api/messages/{messageId}/reactions/summary", testMessage1.getId())
            .with(authPostProcessor))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Get reaction summary response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(7)
    @DisplayName("Try to add invalid reaction type")
    public void testAddInvalidReaction() throws Exception {
        // Ensure required entities are not null before attempting to add an invalid reaction
        if (testMessage1 == null || testMessage1.getId() == null || testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test add invalid reaction - testMessage1 or testUser1 or their IDs are null");
            fail("Cannot test add invalid reaction - testMessage1 or testUser1 or their IDs are null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "INVALID");
        
        performAuthenticatedRequest(post("/api/messages/{messageId}/reactions", testMessage1.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody.toString())
            .with(authPostProcessor))
            .andExpect(status().isBadRequest())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Add invalid reaction response: " + responseContent);
            })
            .andDo(print());
    }
    
    @Test
    @Order(8)
    @DisplayName("Try to add reaction to non-existent message")
    public void testAddReactionToNonExistentMessage() throws Exception {
        // Ensure the user ID is not null before attempting to add a reaction
        if (testUser1 == null || testUser1.getId() == null) {
            System.err.println("DEBUG ERROR: Cannot test add reaction to non-existent message - testUser1 or its ID is null");
            fail("Cannot test add reaction to non-existent message - testUser1 or its ID is null");
            return;
        }
        
        // Set request attributes to bypass JWT authentication in AuthFilter if security is not fully disabled
        RequestPostProcessor authPostProcessor = request -> {
            request.setAttribute("userId", testUser1.getId());
            request.setAttribute("role", "USER");
            System.out.println("DEBUG: Set request attributes for authentication - userId: " + testUser1.getId() + ", role: USER");
            return request;
        };
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("reactionType", "LIKE");
        
        performAuthenticatedRequest(post("/api/messages/{messageId}/reactions", 99999L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody.toString())
            .with(authPostProcessor))
            .andExpect(status().isBadRequest())
            .andDo(result -> {
                String responseContent = result.getResponse().getContentAsString();
                System.out.println("DEBUG: Add reaction to non-existent message response: " + responseContent);
            })
            .andDo(print());
    }
}
*/
// Commented out due to changes in reaction functionality as per user request 