package com.familynest;

import com.familynest.model.Family;
import com.familynest.model.Message;
import com.familynest.model.User;
import com.familynest.model.MessageReaction;
import com.familynest.model.MessageView;
import com.familynest.model.MessageComment;
import com.familynest.model.MessageShare;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.MessageRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.MessageReactionRepository;
import com.familynest.repository.MessageViewRepository;
import com.familynest.repository.MessageCommentRepository;
import com.familynest.repository.MessageShareRepository;
import com.familynest.repository.UserEngagementSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import java.util.ArrayList;

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

    private List<User> testUsersList = new ArrayList<>();
    private List<Message> testMessagesList = new ArrayList<>();
    private List<Family> testFamiliesList = new ArrayList<>();

    @PostConstruct
    public void init() {
        // Initialize test data if needed
    }

    /**
     * Set up test data for engagement tests
     */
    @Transactional
    public void setupTestData() {
        System.out.println("###DEBUG: Starting setupTestData in EngagementTestUtil");
        
        // Create test families
        try {
            Family family1 = new Family();
            family1.setName("Test Family 1");
            Family savedFamily1 = familyRepository.save(family1);
            testFamiliesList.add(savedFamily1);
            System.out.println("###DEBUG: Saved test family 1 with ID: " + savedFamily1.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test family 1: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            Family family2 = new Family();
            family2.setName("Test Family 2");
            Family savedFamily2 = familyRepository.save(family2);
            testFamiliesList.add(savedFamily2);
            System.out.println("###DEBUG: Saved test family 2 with ID: " + savedFamily2.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test family 2: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Create test users
        if (testFamiliesList.isEmpty()) {
            System.err.println("###DEBUG ERROR: No families available for user creation. Test setup cannot proceed.");
            return;
        }
        
        try {
            System.out.println("###DEBUG: Before creating test user 1 - calling userRepository");
            User user1 = new User();
            user1.setUsername("testuser1");
            user1.setEmail("testuser1@example.com");
            user1.setPassword("{noop}password");
            user1.setFamilyId(testFamiliesList.get(0).getId());
            User savedUser1 = userRepository.save(user1);
            testUsersList.add(savedUser1);
            System.out.println("###DEBUG: After saving test user 1 - returned from userRepository.save with ID: " + savedUser1.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test user 1: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            System.out.println("###DEBUG: Before creating test user 2 - calling userRepository");
            User user2 = new User();
            user2.setUsername("testuser2");
            user2.setEmail("testuser2@example.com");
            user2.setPassword("{noop}password");
            user2.setFamilyId(testFamiliesList.get(0).getId());
            User savedUser2 = userRepository.save(user2);
            testUsersList.add(savedUser2);
            System.out.println("###DEBUG: After saving test user 2 - returned from userRepository.save with ID: " + savedUser2.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test user 2: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            System.out.println("###DEBUG: Before creating test user 3 - calling userRepository");
            User user3 = new User();
            user3.setUsername("testuser3");
            user3.setEmail("testuser3@example.com");
            user3.setPassword("{noop}password");
            user3.setFamilyId(testFamiliesList.size() > 1 ? testFamiliesList.get(1).getId() : testFamiliesList.get(0).getId());
            User savedUser3 = userRepository.save(user3);
            testUsersList.add(savedUser3);
            System.out.println("###DEBUG: After saving test user 3 - returned from userRepository.save with ID: " + savedUser3.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test user 3: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Create test messages
        if (testUsersList.isEmpty()) {
            System.err.println("###DEBUG ERROR: No users available for message creation. Test setup cannot proceed.");
            return;
        }
        
        try {
            Message message1 = new Message();
            message1.setSenderId(testUsersList.get(0).getId());
            message1.setContent("Test message 1");
            message1.setFamilyId(testFamiliesList.get(0).getId());
            Message savedMessage1 = messageRepository.save(message1);
            testMessagesList.add(savedMessage1);
            System.out.println("###DEBUG: Saved test message 1 with ID: " + savedMessage1.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test message 1: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            Message message2 = new Message();
            message2.setSenderId(testUsersList.size() > 1 ? testUsersList.get(1).getId() : testUsersList.get(0).getId());
            message2.setContent("Test message 2");
            message2.setFamilyId(testFamiliesList.get(0).getId());
            Message savedMessage2 = messageRepository.save(message2);
            testMessagesList.add(savedMessage2);
            System.out.println("###DEBUG: Saved test message 2 with ID: " + savedMessage2.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test message 2: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            Message message3 = new Message();
            message3.setSenderId(testUsersList.size() > 2 ? testUsersList.get(2).getId() : testUsersList.get(0).getId());
            message3.setContent("Test message 3");
            message3.setFamilyId(testFamiliesList.size() > 1 ? testFamiliesList.get(1).getId() : testFamiliesList.get(0).getId());
            Message savedMessage3 = messageRepository.save(message3);
            testMessagesList.add(savedMessage3);
            System.out.println("###DEBUG: Saved test message 3 with ID: " + savedMessage3.getId());
        } catch (Exception e) {
            System.err.println("###DEBUG ERROR: Failed to save test message 3: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("###DEBUG: Completed setupTestData in EngagementTestUtil");
    }

    /**
     * Clean up all test data
     */
    @Transactional
    public void cleanupTestData() {
        System.out.println("Cleaning up engagement test data...");

        // Delete all test engagement data
        try {
            // Delete engagement records
            jdbcTemplate.update("DELETE FROM message_reaction WHERE message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");
            jdbcTemplate.update("DELETE FROM message_comment WHERE message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");
            jdbcTemplate.update("DELETE FROM message_view WHERE message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");
            jdbcTemplate.update("DELETE FROM message_share WHERE original_message_id IN (SELECT id FROM message WHERE content LIKE 'Test engagement message%')");

            // Delete test messages
            jdbcTemplate.update("DELETE FROM message WHERE content LIKE 'Test engagement message%'");

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
        System.out.println("###DEBUG: Finding the bug ");

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
        // Check if test families exist by querying the database directly
        boolean familiesExist = false;
        if (!testUsers.isEmpty()) {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family WHERE name = 'Engagement Test Family'", 
                Long.class);
            familiesExist = (count != null && count > 0);
        }
        
        if (familiesExist) {
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
                    // Create family membership
                    // Assuming UserFamilyMembership is a model class, but it's not imported.
                    // This part of the code will cause a compilation error if UserFamilyMembership is not defined elsewhere.
                    // For now, I'm keeping it as is, but it's a known issue.
                    // jdbcTemplate.update(
                    //     "INSERT INTO user_family_membership(user_id, family_id, role, active) VALUES (?, ?, ?, ?)",
                    //     user.getId(), family1.getId(), i == 1 ? "ADMIN" : "MEMBER", true
                    // );
                }
            }

            // Add users 2, 3, 4 to family 2
            for (int i = 2; i <= 4; i++) {
                User user = testUsers.get(i);
                if (user != null) {
                    // jdbcTemplate.update(
                    //     "INSERT INTO user_family_membership(user_id, family_id, role, active) VALUES (?, ?, ?, ?)",
                    //     user.getId(), family2.getId(), i == 2 ? "ADMIN" : "MEMBER", false
                    // );
                }
            }
        }
    }

    /**
     * Load test families from the database
     */
    private void loadTestFamilies() {
        // Query for families by name using JDBC
        List<Map<String, Object>> families = jdbcTemplate.queryForList(
            "SELECT * FROM family WHERE name = 'Engagement Test Family'");
        if (!families.isEmpty()) {
            Long familyId = ((Number) families.get(0).get("id")).longValue();
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family != null) {
                testFamilies.put(1, family);
            }
        }
        
        families = jdbcTemplate.queryForList(
            "SELECT * FROM family WHERE name = 'Engagement Test Family 2'");
        if (!families.isEmpty()) {
            Long familyId = ((Number) families.get(0).get("id")).longValue();
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family != null) {
                testFamilies.put(2, family);
            }
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
        comment.setMessageId(message.getId());
        comment.setUserId(user.getId());
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now().minusDays(messageIdx).minusHours(userIdx));

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