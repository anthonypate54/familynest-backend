package com.familynest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import com.familynest.model.User;
import com.familynest.model.Family;
import com.familynest.model.UserFamilyMembership;
import com.familynest.model.UserFamilyMessageSettings;
import com.familynest.model.UserMemberMessageSettings;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import com.familynest.repository.UserMemberMessageSettingsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Utility for setting up and tearing down message preferences test data
 */
@Component
public class MessagePreferencesTestUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(MessagePreferencesTestUtil.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FamilyRepository familyRepository;
    
    @Autowired
    private UserFamilyMembershipRepository membershipRepository;
    
    @Autowired
    private UserFamilyMessageSettingsRepository familySettingsRepository;
    
    @Autowired
    private UserMemberMessageSettingsRepository memberSettingsRepository;
    
    // Store created entities for cleanup
    private List<User> createdUsers = new ArrayList<>();
    private List<Family> createdFamilies = new ArrayList<>();
    private List<UserFamilyMembership> createdMemberships = new ArrayList<>();
    private List<UserFamilyMessageSettings> createdFamilySettings = new ArrayList<>();
    private List<UserMemberMessageSettings> createdMemberSettings = new ArrayList<>();
    
    /**
     * Set up a fresh test database with schema and test data
     */
    @Transactional
    public void setupTestData() {
        logger.info("Setting up message preferences test data");
        
        try {
            // Create test users
            List<User> users = createTestUsers();
            
            // Create test families
            List<Family> families = createTestFamilies(users);
            
            // Create family memberships
            createFamilyMemberships(users, families);
            
            // Set up message preferences (both family-level and member-level)
            createMessagePreferences(users, families);
            
            logger.info("Successfully set up message preferences test data");
        } catch (Exception e) {
            logger.error("Error setting up test data", e);
            throw new RuntimeException("Failed to set up test data", e);
        }
    }
    
    /**
     * Clean up all test data created by this utility
     */
    @Transactional
    public void cleanupTestData() {
        logger.info("Cleaning up message preferences test data");
        
        try {
            // Delete in reverse order of dependencies
            for (UserMemberMessageSettings setting : createdMemberSettings) {
                memberSettingsRepository.delete(setting);
            }
            
            for (UserFamilyMessageSettings setting : createdFamilySettings) {
                familySettingsRepository.delete(setting);
            }
            
            for (UserFamilyMembership membership : createdMemberships) {
                membershipRepository.delete(membership);
            }
            
            for (Family family : createdFamilies) {
                familyRepository.delete(family);
            }
            
            for (User user : createdUsers) {
                userRepository.delete(user);
            }
            
            logger.info("Successfully cleaned up message preferences test data");
        } catch (Exception e) {
            logger.error("Error cleaning up test data", e);
            throw new RuntimeException("Failed to clean up test data", e);
        }
    }
    
    /**
     * Create test users for message preferences testing
     */
    private List<User> createTestUsers() {
        logger.info("Creating test users");
        
        // Create 10 test users
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User user = new User();
            user.setUsername("msguser" + i);
            user.setEmail("msguser" + i + "@test.com");
            user.setPassword("{noop}password"); // {noop} prefix for Spring Security to not encode
            user.setFirstName("Message");
            user.setLastName("User" + i);
            user.setRole("USER");
            // Only set enabled if the method exists
            try {
                user.getClass().getMethod("setEnabled", boolean.class).invoke(user, true);
            } catch (Exception e) {
                // Method doesn't exist, skip it
                logger.debug("setEnabled method not found, skipping");
            }
            
            User savedUser = userRepository.save(user);
            users.add(savedUser);
            createdUsers.add(savedUser);
            logger.info("Created test user: {}", user.getUsername());
        }
        
        return users;
    }
    
    /**
     * Create test families
     */
    private List<Family> createTestFamilies(List<User> users) {
        logger.info("Creating test families");
        
        // Create 3 test families, each created by a different user
        List<Family> families = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Family family = new Family();
            family.setName("Message Test Family " + (i + 1));
            try {
                // Try to set the createdBy field
                family.getClass().getMethod("setCreatedBy", Long.class).invoke(family, users.get(i).getId());
            } catch (Exception e) {
                logger.debug("setCreatedBy(Long) method not found, trying alternative");
                try {
                    // Try alternative method if it exists
                    family.getClass().getMethod("setCreatedBy", User.class).invoke(family, users.get(i));
                } catch (Exception e2) {
                    logger.warn("Could not set family creator", e2);
                }
            }
            
            Family savedFamily = familyRepository.save(family);
            families.add(savedFamily);
            createdFamilies.add(savedFamily);
            logger.info("Created test family: {}", family.getName());
        }
        
        return families;
    }
    
    /**
     * Create family memberships
     * - Family 1: Users 1-5
     * - Family 2: Users 4-8
     * - Family 3: Users 6-10
     */
    private void createFamilyMemberships(List<User> users, List<Family> families) {
        logger.info("Creating family memberships");
        
        // Family 1 (Users 1-5)
        for (int i = 0; i < 5; i++) {
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(users.get(i).getId());
            membership.setFamilyId(families.get(0).getId());
            
            // First user is admin (creator)
            if (i == 0) {
                membership.setRole("ADMIN");
            } else {
                membership.setRole("MEMBER");
            }
            
            membership.setActive(true);
            UserFamilyMembership savedMembership = membershipRepository.save(membership);
            createdMemberships.add(savedMembership);
            logger.info("Added user {} to family {}", users.get(i).getUsername(), families.get(0).getName());
        }
        
        // Family 2 (Users 4-8)
        for (int i = 3; i < 8; i++) {
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(users.get(i).getId());
            membership.setFamilyId(families.get(1).getId());
            
            // User 4 is admin (creator)
            if (i == 3) {
                membership.setRole("ADMIN");
            } else {
                membership.setRole("MEMBER");
            }
            
            membership.setActive(true);
            UserFamilyMembership savedMembership = membershipRepository.save(membership);
            createdMemberships.add(savedMembership);
            logger.info("Added user {} to family {}", users.get(i).getUsername(), families.get(1).getName());
        }
        
        // Family 3 (Users 6-10)
        for (int i = 5; i < 10; i++) {
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(users.get(i).getId());
            membership.setFamilyId(families.get(2).getId());
            
            // User 6 is admin (creator)
            if (i == 5) {
                membership.setRole("ADMIN");
            } else {
                membership.setRole("MEMBER");
            }
            
            membership.setActive(true);
            UserFamilyMembership savedMembership = membershipRepository.save(membership);
            createdMemberships.add(savedMembership);
            logger.info("Added user {} to family {}", users.get(i).getUsername(), families.get(2).getName());
        }
    }
    
    /**
     * Create message preferences (both family-level and member-level)
     * - Some users have family-level preferences
     * - Some users have member-level preferences
     */
    private void createMessagePreferences(List<User> users, List<Family> families) {
        logger.info("Creating message preferences");
        
        // Family-level preferences
        // Some users mute entire families
        UserFamilyMessageSettings settings1 = new UserFamilyMessageSettings();
        settings1.setUserId(users.get(3).getId()); // User 4
        settings1.setFamilyId(families.get(0).getId()); // Family 1
        settings1.setReceiveMessages(false); // Muted
        settings1.setLastUpdated(LocalDateTime.now());
        UserFamilyMessageSettings savedSettings1 = familySettingsRepository.save(settings1);
        createdFamilySettings.add(savedSettings1);
        
        UserFamilyMessageSettings settings2 = new UserFamilyMessageSettings();
        settings2.setUserId(users.get(6).getId()); // User 7
        settings2.setFamilyId(families.get(1).getId()); // Family 2
        settings2.setReceiveMessages(false); // Muted
        settings2.setLastUpdated(LocalDateTime.now());
        UserFamilyMessageSettings savedSettings2 = familySettingsRepository.save(settings2);
        createdFamilySettings.add(savedSettings2);
        
        // Member-level preferences
        // User 1 mutes User 3 in Family 1
        UserMemberMessageSettings memberSettings1 = new UserMemberMessageSettings();
        memberSettings1.setUserId(users.get(0).getId()); // User 1
        memberSettings1.setFamilyId(families.get(0).getId()); // Family 1
        memberSettings1.setMemberUserId(users.get(2).getId()); // User 3
        memberSettings1.setReceiveMessages(false); // Muted
        memberSettings1.setLastUpdated(LocalDateTime.now());
        UserMemberMessageSettings savedMemberSettings1 = memberSettingsRepository.save(memberSettings1);
        createdMemberSettings.add(savedMemberSettings1);
        
        // User 5 mutes User 7 in Family 2
        UserMemberMessageSettings memberSettings2 = new UserMemberMessageSettings();
        memberSettings2.setUserId(users.get(4).getId()); // User 5
        memberSettings2.setFamilyId(families.get(1).getId()); // Family 2
        memberSettings2.setMemberUserId(users.get(6).getId()); // User 7
        memberSettings2.setReceiveMessages(false); // Muted
        memberSettings2.setLastUpdated(LocalDateTime.now());
        UserMemberMessageSettings savedMemberSettings2 = memberSettingsRepository.save(memberSettings2);
        createdMemberSettings.add(savedMemberSettings2);
        
        // User 8 mutes User 9 in Family 3
        UserMemberMessageSettings memberSettings3 = new UserMemberMessageSettings();
        memberSettings3.setUserId(users.get(7).getId()); // User 8
        memberSettings3.setFamilyId(families.get(2).getId()); // Family 3
        memberSettings3.setMemberUserId(users.get(8).getId()); // User 9
        memberSettings3.setReceiveMessages(false); // Muted
        memberSettings3.setLastUpdated(LocalDateTime.now());
        UserMemberMessageSettings savedMemberSettings3 = memberSettingsRepository.save(memberSettings3);
        createdMemberSettings.add(savedMemberSettings3);
        
        logger.info("Created {} family message preferences", createdFamilySettings.size());
        logger.info("Created {} member message preferences", createdMemberSettings.size());
    }
    
    /**
     * Save a user and track it for cleanup
     */
    public User saveUser(User user) {
        logger.info("Saving test user: {}", user.getUsername());
        User savedUser = userRepository.save(user);
        createdUsers.add(savedUser);
        return savedUser;
    }
    
    /**
     * Save a family and track it for cleanup
     */
    public Family saveFamily(Family family) {
        logger.info("Saving test family: {}", family.getName());
        Family savedFamily = familyRepository.save(family);
        createdFamilies.add(savedFamily);
        return savedFamily;
    }
    
    /**
     * Save a membership and track it for cleanup
     */
    public UserFamilyMembership saveMembership(UserFamilyMembership membership) {
        logger.info("Saving test membership: user {} in family {}", 
                membership.getUserId(), membership.getFamilyId());
        UserFamilyMembership savedMembership = membershipRepository.save(membership);
        createdMemberships.add(savedMembership);
        return savedMembership;
    }
    
    /**
     * Save a member message setting and track it for cleanup
     */
    public UserMemberMessageSettings saveMemberMessageSetting(UserMemberMessageSettings setting) {
        logger.info("Saving test member message setting: user {} for member {} in family {}", 
                setting.getUserId(), setting.getMemberUserId(), setting.getFamilyId());
        UserMemberMessageSettings savedSetting = memberSettingsRepository.save(setting);
        createdMemberSettings.add(savedSetting);
        return savedSetting;
    }
    
    /**
     * Get all members of a family
     */
    public List<User> getFamilyMembers(Long familyId) {
        logger.info("Getting members of family: {}", familyId);
        List<UserFamilyMembership> memberships = membershipRepository.findByFamilyId(familyId);
        List<User> members = new ArrayList<>();
        for (UserFamilyMembership membership : memberships) {
            Optional<User> userOpt = userRepository.findById(membership.getUserId());
            if (userOpt.isPresent()) {
                members.add(userOpt.get());
            }
        }
        return members;
    }
    
    /**
     * Get test user by index (1-based)
     */
    public User getTestUser(int index) {
        String username = "msguser" + index;
        List<User> users = userRepository.findAll();
        
        for (User user : users) {
            if (username.equals(user.getUsername())) {
                return user;
            }
        }
        
        throw new RuntimeException("Test user not found: " + username);
    }
    
    /**
     * Get test family by index (1-based)
     */
    public Family getTestFamily(int index) {
        String familyName = "Message Test Family " + index;
        List<Family> families = familyRepository.findAll();
        
        for (Family family : families) {
            if (familyName.equals(family.getName())) {
                return family;
            }
        }
        
        throw new RuntimeException("Test family not found: " + familyName);
    }
    
    /**
     * Execute raw SQL script from resources folder
     */
    public void executeScript(String scriptPath) {
        try {
            Resource resource = new ClassPathResource(scriptPath);
            String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(script);
            logger.info("Executed script: {}", scriptPath);
        } catch (IOException e) {
            logger.error("Failed to read script file: {}", scriptPath, e);
            throw new RuntimeException("Failed to read script file: " + scriptPath, e);
        }
    }
} 