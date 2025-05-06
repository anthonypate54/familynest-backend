package com.familynest;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.familynest.model.Message;
import com.familynest.model.MessageComment;
import com.familynest.model.User;
import com.familynest.repository.MessageCommentRepository;
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
import java.util.Optional;

import org.json.JSONObject;

/**
 * Integration tests for the Comment Controller
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
public class CommentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private EngagementTestUtil testUtil;
    
    @Autowired
    private MessageCommentRepository commentRepository;
    
    @Autowired
    private TestAuthFilter testAuthFilter;
    
    // Test data
    private User testUser1;
    private User testUser2;
    private Message testMessage1;
    private Message testMessage2;
    private MessageComment testComment1;
    private MessageComment testComment2;
    private Long createdCommentId;
    
    @BeforeAll
    public void setUp() {
        // Set up test data
        testUtil.setupTestData();
        
        // Cache test entities for easier access
        testUser1 = testUtil.getTestUser(1);
        testUser2 = testUtil.getTestUser(2);
        testMessage1 = testUtil.getTestMessage(1);
        testMessage2 = testUtil.getTestMessage(2);
        
        // Create some test comments
        testComment1 = new MessageComment();
        testComment1.setMessageId(testMessage1.getId());
        testComment1.setUserId(testUser1.getId());
        testComment1.setContent("Test comment 1");
        testComment1.setCreatedAt(java.time.LocalDateTime.now());
        testComment1 = commentRepository.save(testComment1);
        
        testComment2 = new MessageComment();
        testComment2.setMessageId(testMessage1.getId());
        testComment2.setUserId(testUser2.getId());
        testComment2.setContent("Test comment 2");
        testComment2.setCreatedAt(java.time.LocalDateTime.now());
        testComment2 = commentRepository.save(testComment2);
        
        // Create a reply to testComment1
        MessageComment reply = new MessageComment();
        reply.setMessageId(testMessage1.getId());
        reply.setUserId(testUser2.getId());
        reply.setContent("Reply to comment 1");
        reply.setParentCommentId(testComment1.getId());
        reply.setCreatedAt(java.time.LocalDateTime.now());
        commentRepository.save(reply);
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
    @DisplayName("Get comments for a message")
    public void testGetComments() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/" + testMessage1.getId() + "/comments")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalItems").value(greaterThanOrEqualTo(2)));
    }
    
    @Test
    @Order(2)
    @DisplayName("Get replies for a comment")
    public void testGetCommentReplies() throws Exception {
        setTestUser(testUser1);
        
        performAuthenticatedRequest(
            get("/api/messages/comments/" + testComment1.getId() + "/replies")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replies", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(1)));
    }
    
    @Test
    @Order(3)
    @DisplayName("Add a new comment to a message")
    public void testAddComment() throws Exception {
        setTestUser(testUser1);
        
        long initialCount = commentRepository.countByMessageId(testMessage2.getId());
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("content", "New test comment");
        
        MvcResult result = performAuthenticatedRequest(
            post("/api/messages/" + testMessage2.getId() + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(testMessage2.getId()))
                .andExpect(jsonPath("$.userId").value(testUser1.getId()))
                .andExpect(jsonPath("$.content").value("New test comment"))
                .andReturn();
                
        String responseJson = result.getResponse().getContentAsString();
        JSONObject responseObj = new JSONObject(responseJson);
        createdCommentId = Long.valueOf(responseObj.getLong("id"));
        
        // Verify comment was added to the database
        long newCount = commentRepository.countByMessageId(testMessage2.getId());
        assertEquals(initialCount + 1, newCount, "Comment count should be incremented by 1");
    }
    
    @Test
    @Order(4)
    @DisplayName("Update an existing comment")
    public void testUpdateComment() throws Exception {
        setTestUser(testUser1);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("content", "Updated comment content");
        
        performAuthenticatedRequest(
            put("/api/messages/comments/" + createdCommentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdCommentId))
                .andExpect(jsonPath("$.content").value("Updated comment content"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        
        // Verify comment was updated in the database
        Optional<MessageComment> updatedComment = commentRepository.findById(createdCommentId);
        assertTrue(updatedComment.isPresent(), "Comment should exist");
        assertEquals("Updated comment content", updatedComment.get().getContent(), "Comment content should be updated");
    }
    
    @Test
    @Order(5)
    @DisplayName("Delete a comment")
    public void testDeleteComment() throws Exception {
        setTestUser(testUser1);
        
        // Verify the comment exists before deletion
        Optional<MessageComment> existingComment = commentRepository.findById(createdCommentId);
        assertTrue(existingComment.isPresent(), "Comment should exist before deletion");
        
        performAuthenticatedRequest(
            delete("/api/messages/comments/" + createdCommentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Comment deleted successfully"));
        
        // Verify comment was deleted from the database
        Optional<MessageComment> deletedComment = commentRepository.findById(createdCommentId);
        assertFalse(deletedComment.isPresent(), "Comment should be deleted");
    }
    
    @Test
    @Order(6)
    @DisplayName("Try to update another user's comment")
    public void testUpdateOtherUsersComment() throws Exception {
        setTestUser(testUser2);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("content", "Attempting to update someone else's comment");
        
        performAuthenticatedRequest(
            put("/api/messages/comments/" + testComment1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("authorized")));
    }
    
    @Test
    @Order(7)
    @DisplayName("Try to delete another user's comment")
    public void testDeleteOtherUsersComment() throws Exception {
        setTestUser(testUser2);
        
        performAuthenticatedRequest(
            delete("/api/messages/comments/" + testComment1.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("authorized")));
    }
    
    @Test
    @Order(8)
    @DisplayName("Add a reply to a comment")
    public void testAddCommentReply() throws Exception {
        setTestUser(testUser1);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("content", "This is a reply");
        requestBody.put("parentCommentId", testComment2.getId());
        
        performAuthenticatedRequest(
            post("/api/messages/" + testMessage1.getId() + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(testMessage1.getId()))
                .andExpect(jsonPath("$.userId").value(testUser1.getId()))
                .andExpect(jsonPath("$.content").value("This is a reply"))
                .andExpect(jsonPath("$.parentCommentId").value(testComment2.getId()));
    }
} 