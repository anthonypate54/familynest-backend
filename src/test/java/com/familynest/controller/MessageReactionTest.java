package com.familynest.controller;

import com.familynest.model.Message;
import com.familynest.model.MessageComment;
import com.familynest.model.MessageReaction;
import com.familynest.model.User;
import com.familynest.repository.MessageCommentRepository;
import com.familynest.repository.MessageReactionRepository;
import com.familynest.repository.MessageRepository;
import com.familynest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for message reactions functionality.
 * This test verifies all endpoints related to message and comment reactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testdb")
@Transactional
public class MessageReactionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageCommentRepository commentRepository;

    @Autowired
    private MessageReactionRepository reactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Message testMessage;
    private MessageComment testComment;
    private String authToken;

    @BeforeEach
    public void setup() throws Exception {
        // Clean up any existing test data
        reactionRepository.deleteAll();
        
        // Create test user if not exists
        Optional<User> existingUser = userRepository.findByEmail("test@example.com");
        if (existingUser.isPresent()) {
            testUser = existingUser.get();
        } else {
            testUser = new User();
            testUser.setUsername("testuser");
            testUser.setEmail("test@example.com");
            testUser.setPassword("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG"); // password: password123
            testUser.setFirstName("Test");
            testUser.setLastName("User");
            testUser.setRole("USER");
            testUser = userRepository.save(testUser);
        }

        // Create test message
        testMessage = new Message();
        testMessage.setSenderId(testUser.getId());
        testMessage.setContent("Test message for reaction testing");
        testMessage.setCreatedAt(LocalDateTime.now());
        testMessage = messageRepository.save(testMessage);

        // Create test comment
        testComment = new MessageComment();
        testComment.setParentMessageId(testMessage.getId());
        testComment.setUserId(testUser.getId());
        testComment.setContent("Test comment for reaction testing");
        testComment.setCreatedAt(LocalDateTime.now());
        testComment = commentRepository.save(testComment);

        // Get auth token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        authToken = response.substring(response.indexOf("\"token\":\"") + 9, response.indexOf("\",\""));
    }

    /**
     * Test adding a reaction to a message.
     */
    @Test
    public void testAddMessageReaction() throws Exception {
        // Add a reaction
        mockMvc.perform(post("/api/messages/{messageId}/reactions", testMessage.getId())
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reactionType\":\"smile\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(testMessage.getId()))
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.reactionType").value("smile"));

        // Verify reaction was saved in database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(1, reactions.size());
        assertEquals(testMessage.getId(), reactions.get(0).getMessageId());
        assertEquals(testUser.getId(), reactions.get(0).getUserId());
        assertEquals("smile", reactions.get(0).getReactionType());
    }

    /**
     * Test getting reactions for a message.
     */
    @Test
    public void testGetMessageReactions() throws Exception {
        // Add a reaction first
        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(testMessage.getId());
        reaction.setUserId(testUser.getId());
        reaction.setReactionType("heart");
        reaction.setCreatedAt(LocalDateTime.now());
        reactionRepository.save(reaction);

        // Get reactions
        mockMvc.perform(get("/api/messages/{messageId}/reactions", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions").isArray())
                .andExpect(jsonPath("$.reactions[0].reactionType").value("heart"))
                .andExpect(jsonPath("$.reactions[0].userId").value(testUser.getId()))
                .andExpect(jsonPath("$.total").value(1));
    }

    /**
     * Test removing a reaction from a message.
     */
    @Test
    public void testRemoveMessageReaction() throws Exception {
        // Add a reaction first
        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(testMessage.getId());
        reaction.setUserId(testUser.getId());
        reaction.setReactionType("thumbsup");
        reaction.setCreatedAt(LocalDateTime.now());
        reactionRepository.save(reaction);

        // Remove the reaction
        mockMvc.perform(delete("/api/messages/{messageId}/reactions/{reactionType}", 
                testMessage.getId(), "thumbsup")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reaction removed successfully"));

        // Verify reaction was removed from database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(0, reactions.size());
    }

    /**
     * Test toggling a like on a message.
     */
    @Test
    public void testToggleMessageLike() throws Exception {
        // Toggle like (add)
        mockMvc.perform(post("/api/messages/{messageId}/message_like", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("added"))
                .andExpect(jsonPath("$.type").value("like"))
                .andExpect(jsonPath("$.like_count").value(1));

        // Verify like was saved in database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(1, reactions.size());
        assertEquals("LIKE", reactions.get(0).getReactionType());

        // Toggle like again (remove)
        mockMvc.perform(post("/api/messages/{messageId}/message_like", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("removed"))
                .andExpect(jsonPath("$.like_count").value(0));

        // Verify like was removed from database
        reactions = reactionRepository.findAll();
        assertEquals(0, reactions.size());
    }

    /**
     * Test toggling a love on a message.
     */
    @Test
    public void testToggleMessageLove() throws Exception {
        // Toggle love (add)
        mockMvc.perform(post("/api/messages/{messageId}/message_love", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("added"))
                .andExpect(jsonPath("$.type").value("love"))
                .andExpect(jsonPath("$.love_count").value(1));

        // Verify love was saved in database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(1, reactions.size());
        assertEquals("LOVE", reactions.get(0).getReactionType());

        // Toggle love again (remove)
        mockMvc.perform(post("/api/messages/{messageId}/message_love", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("removed"))
                .andExpect(jsonPath("$.love_count").value(0));

        // Verify love was removed from database
        reactions = reactionRepository.findAll();
        assertEquals(0, reactions.size());
    }

    /**
     * Test toggling a like on a comment.
     */
    @Test
    public void testToggleCommentLike() throws Exception {
        // Toggle like (add)
        mockMvc.perform(post("/api/messages/{commentId}/comment_like", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("added"))
                .andExpect(jsonPath("$.type").value("like"))
                .andExpect(jsonPath("$.like_count").value(1));

        // Verify like was saved in database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(1, reactions.size());
        assertEquals("LIKE", reactions.get(0).getReactionType());
        assertEquals("COMMENT", jdbcTemplate.queryForObject(
                "SELECT target_type FROM message_reaction WHERE id = ?", 
                String.class, reactions.get(0).getId()));

        // Toggle like again (remove)
        mockMvc.perform(post("/api/messages/{commentId}/comment_like", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("removed"))
                .andExpect(jsonPath("$.like_count").value(0));

        // Verify like was removed from database
        reactions = reactionRepository.findAll();
        assertEquals(0, reactions.size());
    }

    /**
     * Test toggling a love on a comment.
     */
    @Test
    public void testToggleCommentLove() throws Exception {
        // Toggle love (add)
        mockMvc.perform(post("/api/messages/{commentId}/comment_love", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("added"))
                .andExpect(jsonPath("$.type").value("love"))
                .andExpect(jsonPath("$.love_count").value(1));

        // Verify love was saved in database
        List<MessageReaction> reactions = reactionRepository.findAll();
        assertEquals(1, reactions.size());
        assertEquals("LOVE", reactions.get(0).getReactionType());
        assertEquals("COMMENT", jdbcTemplate.queryForObject(
                "SELECT target_type FROM message_reaction WHERE id = ?", 
                String.class, reactions.get(0).getId()));

        // Toggle love again (remove)
        mockMvc.perform(post("/api/messages/{commentId}/comment_love", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("removed"))
                .andExpect(jsonPath("$.love_count").value(0));

        // Verify love was removed from database
        reactions = reactionRepository.findAll();
        assertEquals(0, reactions.size());
    }

    /**
     * Test that the database schema correctly enforces the uniqueness constraint.
     * A user should not be able to add the same reaction type to the same message twice.
     */
    @Test
    public void testReactionUniqueness() {
        // Add a reaction
        MessageReaction reaction1 = new MessageReaction();
        reaction1.setMessageId(testMessage.getId());
        reaction1.setUserId(testUser.getId());
        reaction1.setReactionType("LIKE");
        reaction1.setCreatedAt(LocalDateTime.now());
        reactionRepository.save(reaction1);

        // Try to add the same reaction again
        MessageReaction reaction2 = new MessageReaction();
        reaction2.setMessageId(testMessage.getId());
        reaction2.setUserId(testUser.getId());
        reaction2.setReactionType("LIKE");
        reaction2.setCreatedAt(LocalDateTime.now());
        
        // This should throw an exception due to uniqueness constraint
        assertThrows(Exception.class, () -> {
            reactionRepository.save(reaction2);
            reactionRepository.flush();
        });
    }

    /**
     * Test that the message like count is updated correctly.
     */
    @Test
    public void testMessageLikeCountUpdate() throws Exception {
        // Toggle like (add)
        mockMvc.perform(post("/api/messages/{messageId}/message_like", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify message like_count was updated
        Message updatedMessage = messageRepository.findById(testMessage.getId()).orElseThrow();
        assertEquals(1, updatedMessage.getLikeCount());

        // Toggle like again (remove)
        mockMvc.perform(post("/api/messages/{messageId}/message_like", testMessage.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify message like_count was updated
        updatedMessage = messageRepository.findById(testMessage.getId()).orElseThrow();
        assertEquals(0, updatedMessage.getLikeCount());
    }

    /**
     * Test that the comment like count is updated correctly.
     */
    @Test
    public void testCommentLikeCountUpdate() throws Exception {
        // Toggle like (add)
        mockMvc.perform(post("/api/messages/{commentId}/comment_like", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify comment like_count was updated
        MessageComment updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
        assertEquals(1, updatedComment.getLikeCount());

        // Toggle like again (remove)
        mockMvc.perform(post("/api/messages/{commentId}/comment_like", testComment.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify comment like_count was updated
        updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
        assertEquals(0, updatedComment.getLikeCount());
    }
}
