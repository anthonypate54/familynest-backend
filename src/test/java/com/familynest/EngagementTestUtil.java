package com.familynest;

import com.familynest.model.*;
import com.familynest.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test utility for social engagement features
 */
@Component
public class EngagementTestUtil {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageReactionRepository reactionRepository;

    @Autowired
    private MessageViewRepository viewRepository;

    @Autowired
    private MessageCommentRepository commentRepository;

    @Autowired
    private MessageShareRepository shareRepository;

    @Autowired
    private UserEngagementSettingsRepository settingsRepository;

    // Maps to store test entities
    private Map<Integer, User> testUsers = new HashMap<>();
    private Map<Integer, Family> testFamilies = new HashMap<>();
    private Map<Integer, Message> testMessages = new HashMap<>();
    private Map<Integer, MessageReaction> testReactions = new HashMap<>();
    private Map<Integer, MessageView> testViews = new HashMap<>();
    private Map<Integer, MessageComment> testComments = new HashMap<>();
    private Map<Integer, MessageShare> testShares = new HashMap<>();

    /**
     * Set up test data for engagement tests
     */
    @Transactional
    public void setupTestData() {
        System.out.println("Setting up engagement test data...");

        // Create test users if not already present
        createTestUsers();

        // Create test families if not already present
        createTestFamilies();

        // Create test messages
        createTestMessages();

        // Create test engagement data
        createTestEngagementData();

        System.out.println("Engagement test data setup completed.");
    }

    /**
     * Clean up all test data
     */
    @Transactional
    public void cleanupTestData() {
        System.out.println("Cleaning up engagement test data...");

        // Delete all test engagement data
        try {
            // First, delete child comments (those with parent_comment_id)
            jdbcTemplate.update(
                "DELETE FROM message_comment WHERE parent_comment_id IS NOT NULL AND message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");

            // Then delete parent comments
            jdbcTemplate.update(
                "DELETE FROM message_comment WHERE parent_comment_id IS NULL AND message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");

            // Delete other engagement records
            jdbcTemplate.update(
                "DELETE FROM message_reaction WHERE message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");
            // Removed message_view table - no longer need to clean up view tracking data
            jdbcTemplate.update(
                "DELETE FROM message_share WHERE original_message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");

            // Delete test messages
            jdbcTemplate.update(
                "DELETE FROM message WHERE content LIKE 'Test engagement message%'");

            // Clear the maps
            testMessages.clear();
            testReactions.clear();
            testViews.clear();
            testComments.clear();
            testShares.clear();

            System.out.println("Engagement test data cleaned up successfully");
        } catch (Exception e) {
            System.err.println("Error during engagement test data cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create test users for testing
     */
    private void createTestUsers() {
        // Check if we already have test users
        User existingUser = userRepository.findByUsername("engagementuser1");
        if (existingUser != null) {
            // Load test users from the database
            loadTestUsers();
            return;
        }

        // Create test users
        for (int i = 1; i <= 5; i++) {
            User user = new User();
            user.setUsername("engagementuser" + i);
            user.setEmail("engagementuser" + i + "@test.com");
            user.setPassword("{noop}password");
            user.setFirstName("Engagement");
            user.setLastName("User" + i);
            user.setRole("USER");

            user = userRepository.save(user);
            testUsers.put(i, user);
        }
    }

    /**
     * Load test users from the database
     */
    private void loadTestUsers() {
        for (int i = 1; i <= 5; i++) {
            User user = userRepository.findByUsername("engagementuser" + i);
            if (user != null) {
                testUsers.put(i, user);
            }
        }
    }

    /**
     * Create test families for testing
     */
    private void createTestFamilies() {
        // Check if test families exist
        if (!testUsers.isEmpty() && !familyRepository.findByName("Engagement Test Family").isEmpty()) {
            // Load test families from the database
            loadTestFamilies();
            return;
        }

        // Create test family 1
        Family family1 = new Family();
        family1.setName("Engagement Test Family");
        family1.setCreatedBy(testUsers.get(1));
        family1 = familyRepository.save(family1);
        testFamilies.put(1, family1);

        // Create test family 2
        Family family2 = new Family();
        family2.setName("Engagement Test Family 2");
        family2.setCreatedBy(testUsers.get(2));
        family2 = familyRepository.save(family2);
        testFamilies.put(2, family2);

        // Add users to families (using direct repository operations)
        if (!testUsers.isEmpty()) {
            // Add users to family 1
            for (int i = 1; i <= 5; i++) {
                User user = testUsers.get(i);
                if (user != null) {
                    // Create family membership using the correct column name (is_active)
                    jdbcTemplate.update(
                        "INSERT INTO user_family_membership(user_id, family_id, role, is_active) VALUES (?, ?, ?, ?)",
                        user.getId(), family1.getId(), i == 1 ? "ADMIN" : "MEMBER", true
                    );
                }
            }

            // Add users 2, 3, 4 to family 2
            for (int i = 2; i <= 4; i++) {
                User user = testUsers.get(i);
                if (user != null) {
                    jdbcTemplate.update(
                        "INSERT INTO user_family_membership(user_id, family_id, role, is_active) VALUES (?, ?, ?, ?)",
                        user.getId(), family2.getId(), i == 2 ? "ADMIN" : "MEMBER", false
                    );
                }
            }
        }
    }

    /**
     * Load test families from the database
     */
    private void loadTestFamilies() {
        List<Family> families = familyRepository.findByName("Engagement Test Family");
        if (!families.isEmpty()) {
            testFamilies.put(1, families.get(0));
        }

        families = familyRepository.findByName("Engagement Test Family 2");
        if (!families.isEmpty()) {
            testFamilies.put(2, families.get(0));
        }
    }

    /**
     * Create test messages for the engagement tests
     */
    private void createTestMessages() {
        // First check if test messages already exist
        List<Message> existingMessages = messageRepository.findByFamilyId(getTestFamily(1).getId());
        boolean hasTestMessages = existingMessages.stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().startsWith("Test engagement message"));

        if (hasTestMessages) {
            // Load existing test messages
            loadTestMessages();
            return;
        }

        // Create test messages
        for (int i = 1; i <= 5; i++) {
            User sender = getTestUser(i % 5 + 1);
            Family family = getTestFamily(i <= 3 ? 1 : 2);

            Message message = new Message();
            message.setContent("Test engagement message " + i);
            message.setSenderUsername(sender.getUsername());
            message.setSenderId(sender.getId());
            message.setUserId(sender.getId());
            message.setFamilyId(family.getId());
            message.setTimestamp(LocalDateTime.now().minusDays(i));

            message = messageRepository.save(message);
            testMessages.put(i, message);
        }
    }

    /**
     * Load test messages from the database
     */
    private void loadTestMessages() {
        int count = 1;
        for (Family family : testFamilies.values()) {
            List<Message> messages = messageRepository.findByFamilyId(family.getId());
            for (Message message : messages) {
                if (message.getContent() != null && message.getContent().startsWith("Test engagement message")) {
                    testMessages.put(count++, message);
                    if (count > 5) break;
                }
            }
            if (count > 5) break;
        }
    }

    /**
     * Create test engagement data - reactions, comments, views, etc.
     */
    private void createTestEngagementData() {
        // Check if we already have test reactions
        if (!reactionRepository.findByMessageId(getTestMessage(1).getId()).isEmpty()) {
            return; // Already have engagement data
        }

        // Create reactions
        createTestReactions();

        // Create views
        createTestViews();

        // Create comments
        createTestComments();

        // Create shares
        createTestShares();
    }

    /**
     * Create test reactions
     */
    private void createTestReactions() {
        // Message 1: User 1 likes, User 2 loves, User 3 laughs
        addReaction(1, 1, "LIKE");
        addReaction(1, 2, "LOVE");
        addReaction(1, 3, "LAUGH");

        // Message 2: User 1 and User 3 like
        addReaction(2, 1, "LIKE");
        addReaction(2, 3, "LIKE");

        // Message 3: No reactions

        // Message 4: User 2 likes
        addReaction(4, 2, "LIKE");

        // Message 5: User 1 loves, User 4 likes
        addReaction(5, 1, "LOVE");
        addReaction(5, 4, "LIKE");
    }

    /**
     * Create test views
     */
    private void createTestViews() {
        // Message 1: Viewed by Users 1, 2, 3
        addView(1, 1);
        addView(1, 2);
        addView(1, 3);

        // Message 2: Viewed by Users 1, 3, 4, 5
        addView(2, 1);
        addView(2, 3);
        addView(2, 4);
        addView(2, 5);

        // Message 3: Viewed by User 2 only
        addView(3, 2);

        // Message 4: Viewed by Users 2, 3, 4
        addView(4, 2);
        addView(4, 3);
        addView(4, 4);

        // Message 5: No views
    }

    /**
     * Create test comments
     */
    private void createTestComments() {
        // Message 1: Two comments from different users
        int commentId1 = addComment(1, 2, "Great message!", null);
        int commentId2 = addComment(1, 3, "I agree!", null);
        
        // Add a reply to the first comment
        addComment(1, 1, "Thanks!", commentId1);

        // Message 2: One comment
        addComment(2, 4, "This is interesting.", null);

        // Message 3: No comments

        // Message 4: One comment with one reply
        int commentId3 = addComment(4, 3, "What do you think?", null);
        addComment(4, 2, "I think it's good.", commentId3);

        // Message 5: No comments
    }

    /**
     * Create test shares
     */
    private void createTestShares() {
        // Message 1: Shared by User 2 to Family 2
        addShare(1, 2, 2);

        // Message 2: Shared by User 1 to Family 2
        addShare(2, 1, 2);

        // Message 3, 4, 5: No shares
    }

    /**
     * Add a reaction to a message
     */
    private int addReaction(int messageIdx, int userIdx, String reactionType) {
        Message message = getTestMessage(messageIdx);
        User user = getTestUser(userIdx);

        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(message.getId());
        reaction.setUserId(user.getId());
        reaction.setReactionType(reactionType);
        reaction.setCreatedAt(LocalDateTime.now().minusDays(messageIdx).minusHours(userIdx));

        reaction = reactionRepository.save(reaction);
        int idx = testReactions.size() + 1;
        testReactions.put(idx, reaction);
        return idx;
    }

    /**
     * Add a view to a message
     */
    private int addView(int messageIdx, int userIdx) {
        Message message = getTestMessage(messageIdx);
        User user = getTestUser(userIdx);

        MessageView view = new MessageView();
        view.setMessageId(message.getId());
        view.setUserId(user.getId());
        view.setViewedAt(LocalDateTime.now().minusDays(messageIdx).minusHours(userIdx));

        view = viewRepository.save(view);
        int idx = testViews.size() + 1;
        testViews.put(idx, view);
        return idx;
    }

    /**
     * Add a comment to a message
     */
    private int addComment(int messageIdx, int userIdx, String content, Integer parentCommentIdx) {
        Message message = getTestMessage(messageIdx);
        User user = getTestUser(userIdx);

        MessageComment comment = new MessageComment();
        comment.setParentMessageId(message.getId());
        comment.setSenderId(user.getId());
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now().minusDays(messageIdx).minusHours(userIdx));
        // Set family_id from the associated message's family
        comment.setFamilyId(message.getFamilyId());

        if (parentCommentIdx != null) {
            comment.setParentCommentId(testComments.get(parentCommentIdx).getId());
        }

        comment = commentRepository.save(comment);
        int idx = testComments.size() + 1;
        testComments.put(idx, comment);
        return idx;
    }

    /**
     * Add a share record
     */
    private int addShare(int messageIdx, int userIdx, int familyIdx) {
        Message message = getTestMessage(messageIdx);
        User user = getTestUser(userIdx);
        Family family = getTestFamily(familyIdx);

        MessageShare share = new MessageShare();
        share.setOriginalMessageId(message.getId());
        share.setSharedByUserId(user.getId());
        share.setSharedToFamilyId(family.getId());
        share.setSharedAt(LocalDateTime.now().minusDays(messageIdx).minusHours(userIdx));

        share = shareRepository.save(share);
        int idx = testShares.size() + 1;
        testShares.put(idx, share);
        return idx;
    }

    // Getter methods for test entities

    public User getTestUser(int idx) {
        return testUsers.get(idx);
    }

    public Family getTestFamily(int idx) {
        return testFamilies.get(idx);
    }

    public Message getTestMessage(int idx) {
        return testMessages.get(idx);
    }

    public MessageReaction getTestReaction(int idx) {
        return testReactions.get(idx);
    }

    public MessageView getTestView(int idx) {
        return testViews.get(idx);
    }

    public MessageComment getTestComment(int idx) {
        return testComments.get(idx);
    }

    public MessageShare getTestShare(int idx) {
        return testShares.get(idx);
    }
} 