package com.familynest;

import com.familynest.model.*;
import com.familynest.repository.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class FamilyMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FamilyRepository familyRepository;
    
    @Autowired
    private UserFamilyMembershipRepository membershipRepository;
    
    @Autowired
    private UserMemberMessageSettingsRepository userMemberMessageSettingsRepository;
    
    // Test entities
    private User testUser1;
    private User testUser2;
    private User testUser11;
    private Family family1;
    
    @BeforeAll
    public void setUp() {
        // Create test users
        testUser1 = createUser("testuser1", "Test", "User1");
        testUser2 = createUser("testuser2", "Test", "User2");
        testUser11 = createUser("testuser11", "Test", "User11");
        
        // Create a test family
        family1 = new Family();
        family1.setName("Test Family 1");
        family1.setCreatedBy(testUser1);
        family1 = familyRepository.save(family1);
        
        // Add user1 as admin
        createMembership(testUser1, family1, "ADMIN");
        
        // Add user2 and user11 as members
        createMembership(testUser2, family1, "MEMBER");
        createMembership(testUser11, family1, "MEMBER");
        
        // Create member message settings
        UserMemberMessageSettings settings = new UserMemberMessageSettings();
        settings.setUserId(testUser1.getId());
        settings.setFamilyId(family1.getId());
        settings.setMemberUserId(testUser11.getId());
        settings.setReceiveMessages(true);
        settings.setLastUpdated(LocalDateTime.now());
        userMemberMessageSettingsRepository.save(settings);
    }
    
    @AfterAll
    public void tearDown() {
        // Clean up test data
        userMemberMessageSettingsRepository.deleteAll();
        membershipRepository.deleteAll();
        familyRepository.deleteAll();
        userRepository.deleteAll();
    }
    
    @Test
    public void testMemberMessagePreferencesExist() {
        // Verify that member message preferences exist for testuser1 and all members
        Long testUser1Id = testUser1.getId();
        Long family1Id = family1.getId();
        
        // Check that preferences exist for all members in family1
        List<UserMemberMessageSettings> preferences = userMemberMessageSettingsRepository
                .findByUserIdAndFamilyId(testUser1Id, family1Id);
                
        // We should have at least one preference
        assertTrue(preferences.size() >= 1, 
                "Should have at least 1 member preference for testuser1, but found " + preferences.size());
                
        // Check that testuser1 has a preference for receiving messages from testuser11
        Long testUser11Id = testUser11.getId();
        boolean hasPreferenceForUser11 = preferences.stream()
                .anyMatch(pref -> pref.getMemberUserId().equals(testUser11Id));
                
        assertTrue(hasPreferenceForUser11, 
                "testuser1 should have message preference for testuser11");
    }
    
    // Helper methods
    
    private User createUser(String username, String firstName, String lastName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("$2a$10$h.dl5J86rGH7I8bD9bZeZeri3YeW5q1mHQQzMuV1QEvt0U4Ex.9tK"); // "password"
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole("USER");
        return userRepository.save(user);
    }
    
    private UserFamilyMembership createMembership(User user, Family family, String role) {
        UserFamilyMembership membership = new UserFamilyMembership();
        membership.setUserId(user.getId());
        membership.setFamilyId(family.getId());
        membership.setRole(role);
        membership.setActive(true);
        return membershipRepository.save(membership);
    }
} 