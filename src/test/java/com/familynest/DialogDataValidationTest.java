package com.familynest;

import com.familynest.model.User;
import com.familynest.model.Family;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.service.UserService;
import com.familynest.controller.MessagePreferencesController;
import com.familynest.controller.MemberMessagePreferencesController;
import com.familynest.controller.UserController;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Test that verifies the data used by the dialog components
 * Shows detailed data about the relationships between users and families
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class DialogDataValidationTest {

    @Autowired
    private MessagePreferencesController messagePreferencesController;
    
    @Autowired
    private MemberMessagePreferencesController memberMessagePreferencesController;
    
    @Autowired
    private UserController userController;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FamilyRepository familyRepository;
    
    @Autowired
    private UserFamilyMembershipRepository membershipRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MessagePreferencesTestUtil testUtil;
    
    private User testUser1;
    
    @BeforeAll
    public void setUp() {
        // Make sure test data is set up
        testUtil.setupTestData();
        
        // Get a reference to testUser1 for most operations
        testUser1 = testUtil.getTestUser(1);
        System.out.println("\n====== Test User Setup ======");
        System.out.println("Test User ID: " + testUser1.getId());
        System.out.println("Username: " + testUser1.getUsername());
        System.out.println("First Name: " + testUser1.getFirstName());
        System.out.println("Last Name: " + testUser1.getLastName());
        System.out.println("Role: " + testUser1.getRole());
    }
    
    @AfterAll
    public void tearDown() {
        testUtil.cleanupTestData();
    }
    
    @Test
    public void testFamilyMessagePreferencesData() {
        System.out.println("\n====== FAMILY MESSAGE PREFERENCES DATA ======");
        
        // 1. Get family message preferences for user
        ResponseEntity<List<Map<String, Object>>> response = 
                messagePreferencesController.getMessagePreferences(
                        testUser1.getId(), 
                        "test-token",  // not used in test mode
                        null);  // HttpServletRequest is not needed in test
                        
        List<Map<String, Object>> preferences = response.getBody();
        
        System.out.println("User ID: " + testUser1.getId() + " (" + testUser1.getUsername() + ")");
        System.out.println("Number of family preferences: " + (preferences != null ? preferences.size() : 0));
        
        // 2. Show all family preferences
        System.out.println("\n--- Family Preferences ---");
        if (preferences == null || preferences.isEmpty()) {
            System.out.println("No family preferences found");
            return;
        }
        
        for (Map<String, Object> pref : preferences) {
            Number familyIdObj = (Number) pref.get("familyId");
            String familyName = (String) pref.get("familyName");
            Boolean receiveMessages = (Boolean) pref.get("receiveMessages");
            
            if (familyIdObj == null) {
                System.out.println("Missing familyId in preference");
                continue;
            }
            
            Long familyId = familyIdObj.longValue();
            
            System.out.println("Family ID: " + familyId + 
                              ", Name: " + familyName + 
                              ", Receive Messages: " + receiveMessages);
        }
        
        // 3. Get family memberships data directly from database - owners and member counts
        System.out.println("\n--- Family Ownership Info ---");
        List<Map<String, Object>> familyData = jdbcTemplate.queryForList(
                "SELECT f.id, f.name, u.id as owner_id, u.username as owner_username, " +
                "COUNT(m.user_id) as member_count " +
                "FROM family f " +
                "JOIN app_user u ON f.created_by = u.id " +
                "JOIN user_family_membership m ON f.id = m.family_id " +
                "WHERE f.id IN (SELECT family_id FROM user_family_membership WHERE user_id = ?) " +
                "GROUP BY f.id, f.name, u.id, u.username",
                testUser1.getId());
                
        if (familyData.isEmpty()) {
            System.out.println("No family data found in database");
        } else {
            for (Map<String, Object> family : familyData) {
                System.out.println("Family ID: " + family.get("id") + 
                                  ", Name: " + family.get("name") + 
                                  ", Owner: " + family.get("owner_username") + " (ID: " + family.get("owner_id") + ")" +
                                  ", Member Count: " + family.get("member_count"));
            }
        }
    }
    
    @Test
    public void testMemberMessagePreferencesData() {
        System.out.println("\n====== MEMBER MESSAGE PREFERENCES DATA ======");
        
        // 1. Get member message preferences for user
        ResponseEntity<List<Map<String, Object>>> response = 
                memberMessagePreferencesController.getMemberMessagePreferences(
                        testUser1.getId(), 
                        "test-token",  // not used in test mode
                        null);  // HttpServletRequest is not needed in test
                        
        List<Map<String, Object>> preferences = response.getBody();
        
        System.out.println("User ID: " + testUser1.getId() + " (" + testUser1.getUsername() + ")");
        System.out.println("Number of member preferences: " + (preferences != null ? preferences.size() : 0));
        
        // Check if preferences exist
        if (preferences == null || preferences.isEmpty()) {
            System.out.println("No member preferences found");
            return;
        }
        
        // 2. Group preferences by family
        Map<Long, List<Map<String, Object>>> preferencesByFamily = 
                preferences.stream()
                        .filter(pref -> pref.get("familyId") instanceof Number)  // Filter out entries without familyId
                        .collect(Collectors.groupingBy(
                                pref -> ((Number) pref.get("familyId")).longValue()));
                        
        System.out.println("\n--- Member Preferences By Family ---");
        if (preferencesByFamily.isEmpty()) {
            System.out.println("No member preferences by family found");
            return;
        }
        
        for (Map.Entry<Long, List<Map<String, Object>>> entry : preferencesByFamily.entrySet()) {
            Long familyId = entry.getKey();
            List<Map<String, Object>> memberPrefs = entry.getValue();
            
            // Get family name
            String familyName = "Unknown";
            if (!memberPrefs.isEmpty() && memberPrefs.get(0).containsKey("memberOfFamilyName")) {
                familyName = (String) memberPrefs.get(0).get("memberOfFamilyName");
            }
            
            System.out.println("\nFamily ID: " + familyId + ", Name: " + familyName);
            System.out.println("Member preferences count: " + memberPrefs.size());
            
            // Get members in this family
            List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                    "SELECT m.user_id, u.username, u.first_name, u.last_name, m.role, " +
                    "CASE WHEN f.created_by = u.id THEN 'YES' ELSE 'NO' END as is_owner " +
                    "FROM user_family_membership m " +
                    "JOIN app_user u ON m.user_id = u.id " +
                    "JOIN family f ON m.family_id = f.id " +
                    "WHERE m.family_id = ?",
                    familyId);
                    
            System.out.println("Total members in family: " + familyMembers.size());
            
            // Print member preferences
            for (Map<String, Object> pref : memberPrefs) {
                Number memberIdObj = (Number) pref.get("memberUserId");
                if (memberIdObj == null) {
                    System.out.println("  Missing memberUserId in preference");
                    continue;
                }
                
                Long memberId = memberIdObj.longValue();
                String memberUsername = (String) pref.get("memberUsername");
                String memberFirstName = (String) pref.get("memberFirstName");
                String memberLastName = (String) pref.get("memberLastName");
                Boolean receiveMessages = (Boolean) pref.get("receiveMessages");
                
                // Find if this member is an owner
                boolean isOwner = false;
                for (Map<String, Object> member : familyMembers) {
                    Number userIdObj = (Number) member.get("user_id");
                    if (userIdObj != null && 
                        memberId.equals(userIdObj.longValue()) &&
                        "YES".equals(member.get("is_owner"))) {
                        isOwner = true;
                        break;
                    }
                }
                
                System.out.println("  Member ID: " + memberId + 
                                  ", Name: " + memberFirstName + " " + memberLastName +
                                  " (" + memberUsername + ")" +
                                  ", Receive Messages: " + receiveMessages +
                                  (isOwner ? ", IS FAMILY OWNER" : ""));
            }
        }
    }
    
    @Test
    public void testMemberFamilyOwnershipData() {
        System.out.println("\n====== MEMBER FAMILY OWNERSHIP DATA ======");
        
        // Get family members for the families user is in
        List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                "SELECT DISTINCT u.id, u.username, u.first_name, u.last_name " +
                "FROM app_user u " +
                "JOIN user_family_membership m ON u.id = m.user_id " +
                "WHERE m.family_id IN (SELECT family_id FROM user_family_membership WHERE user_id = ?) " +
                "AND u.id != ?",
                testUser1.getId(), testUser1.getId());
                
        System.out.println("Total unique family members across all families: " + familyMembers.size());
        
        if (familyMembers.isEmpty()) {
            System.out.println("No family members found");
            return;
        }
        
        // For each member, check if they own any families
        System.out.println("\n--- Family Members and Their Owned Families ---");
        for (Map<String, Object> member : familyMembers) {
            Number memberIdObj = (Number) member.get("id");
            if (memberIdObj == null) {
                System.out.println("Missing member ID");
                continue;
            }
            
            Long memberId = memberIdObj.longValue();
            String memberName = member.get("first_name") + " " + member.get("last_name") + 
                               " (" + member.get("username") + ")";
                               
            List<Map<String, Object>> ownedFamilies = jdbcTemplate.queryForList(
                    "SELECT f.id, f.name, COUNT(m.user_id) as member_count " +
                    "FROM family f " +
                    "JOIN user_family_membership m ON f.id = m.family_id " +
                    "WHERE f.created_by = ? " +
                    "GROUP BY f.id, f.name",
                    memberId);
                    
            if (ownedFamilies.isEmpty()) {
                System.out.println(memberName + " (ID: " + memberId + ") - Does not own any families");
            } else {
                System.out.println(memberName + " (ID: " + memberId + ") - Owns " + ownedFamilies.size() + " families:");
                
                for (Map<String, Object> family : ownedFamilies) {
                    Number familyIdObj = (Number) family.get("id");
                    if (familyIdObj == null) {
                        System.out.println("  Missing family ID");
                        continue;
                    }
                    
                    Long familyId = familyIdObj.longValue();
                    String familyName = (String) family.get("name");
                    Number memberCountObj = (Number) family.get("member_count");
                    Long memberCount = memberCountObj != null ? memberCountObj.longValue() : 0;
                    
                    System.out.println("  Family ID: " + familyId + 
                                      ", Name: " + familyName + 
                                      ", Member Count: " + memberCount);
                }
            }
        }
    }
    
    @Test
    public void testDialogSequenceWithUpdates() {
        System.out.println("\n====== DIALOG SEQUENCE WITH UPDATES ======");
        
        // 1. First get family preferences (like FamiliesMessageDialog)
        System.out.println("\n--- Step 1: Get Family Preferences (FamiliesMessageDialog) ---");
        ResponseEntity<List<Map<String, Object>>> familyPrefsResponse = 
                messagePreferencesController.getMessagePreferences(
                        testUser1.getId(), "test-token", null);
                        
        List<Map<String, Object>> familyPrefs = familyPrefsResponse.getBody();
        if (familyPrefs == null) {
            familyPrefs = new ArrayList<>();
        }
        
        System.out.println("Found " + familyPrefs.size() + " family preferences");
        
        if (familyPrefs.isEmpty()) {
            System.out.println("No families found! Test cannot continue.");
            return;
        }
        
        // Check if the first preference has a valid familyId
        Map<String, Object> firstPref = familyPrefs.get(0);
        Number selectedFamilyIdObj = (Number) firstPref.get("familyId");
        if (selectedFamilyIdObj == null) {
            System.out.println("First preference is missing familyId! Test cannot continue.");
            return;
        }
        
        // Select the first family
        Long selectedFamilyId = selectedFamilyIdObj.longValue();
        String selectedFamilyName = (String) firstPref.get("familyName");
        Boolean currentReceiveSetting = (Boolean) firstPref.get("receiveMessages");
        
        if (currentReceiveSetting == null) {
            currentReceiveSetting = true; // Default if not set
        }
        
        System.out.println("Selected family ID: " + selectedFamilyId + 
                          ", Name: " + selectedFamilyName + 
                          ", Current setting: " + currentReceiveSetting);
        
        // 2. Update family preference (toggle the current setting)
        System.out.println("\n--- Step 2: Update Family Preference ---");
        Boolean newReceiveSetting = !currentReceiveSetting;
        
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("familyId", selectedFamilyId);
        updateRequest.put("receiveMessages", newReceiveSetting);
        
        ResponseEntity<Map<String, Object>> updateResponse = 
                messagePreferencesController.updateMessagePreferences(
                        testUser1.getId(), "test-token", updateRequest, null);
                        
        Map<String, Object> updatedPref = updateResponse.getBody();
        if (updatedPref == null) {
            System.out.println("No response from update! Test cannot continue.");
            return;
        }
        
        System.out.println("Family preference updated: " + updatedPref);
        System.out.println("New receive setting: " + updatedPref.get("receiveMessages") + 
                          " (was: " + currentReceiveSetting + ")");
        
        // 3. Now test the MemberMessageDialog flow
        System.out.println("\n--- Step 3: Get Family Members (MemberMessageDialog) ---");
        ResponseEntity<List<Map<String, Object>>> membersResponse = 
                userController.getFamilyMembers(
                        testUser1.getId(), 
                        "Bearer test-token");
                        
        List<Map<String, Object>> familyMembers = membersResponse.getBody();
        if (familyMembers == null) {
            familyMembers = new ArrayList<>();
        }
        
        System.out.println("Found " + familyMembers.size() + " members in family " + selectedFamilyName);
        
        if (familyMembers.size() <= 1) {
            System.out.println("Not enough family members to test member preferences!");
            return;
        }
        
        // Find a member who is not the test user
        Map<String, Object> otherMember = null;
        for (Map<String, Object> member : familyMembers) {
            Number memberIdObj = (Number) member.get("userId");
            if (memberIdObj != null && !Objects.equals(memberIdObj.longValue(), testUser1.getId())) {
                otherMember = member;
                break;
            }
        }
        
        if (otherMember == null) {
            System.out.println("Could not find another member in the family!");
            return;
        }
        
        Number otherMemberIdObj = (Number) otherMember.get("userId");
        if (otherMemberIdObj == null) {
            System.out.println("Found a member but it has a null userId!");
            return;
        }
        
        Long otherMemberId = otherMemberIdObj.longValue();
        String otherMemberName = otherMember.get("firstName") + " " + otherMember.get("lastName") + 
                                " (" + otherMember.get("username") + ")";
                                
        System.out.println("Selected member ID: " + otherMemberId + ", Name: " + otherMemberName);
        
        // 4. Get member message preferences
        System.out.println("\n--- Step 4: Get Member Message Preferences ---");
        ResponseEntity<List<Map<String, Object>>> memberPrefsResponse = 
                memberMessagePreferencesController.getMemberMessagePreferences(
                        testUser1.getId(), "test-token", null);
                        
        List<Map<String, Object>> memberPrefs = memberPrefsResponse.getBody();
        if (memberPrefs == null) {
            memberPrefs = new ArrayList<>();
        }
        
        // Find preference for the selected member in the selected family
        Map<String, Object> selectedMemberPref = null;
        for (Map<String, Object> pref : memberPrefs) {
            Number prefFamilyIdObj = (Number) pref.get("familyId");
            Number prefMemberIdObj = (Number) pref.get("memberUserId");
            
            if (prefFamilyIdObj != null && prefMemberIdObj != null &&
                Objects.equals(prefFamilyIdObj.longValue(), selectedFamilyId) && 
                Objects.equals(prefMemberIdObj.longValue(), otherMemberId)) {
                selectedMemberPref = pref;
                break;
            }
        }
        
        Boolean memberCurrentReceiveSetting = true; // Default if no preference exists
        if (selectedMemberPref != null && selectedMemberPref.get("receiveMessages") != null) {
            memberCurrentReceiveSetting = (Boolean) selectedMemberPref.get("receiveMessages");
            System.out.println("Found existing preference for member: " + memberCurrentReceiveSetting);
        } else {
            System.out.println("No existing preference found for this member, using default: " + memberCurrentReceiveSetting);
        }
        
        // 5. Update member preference (toggle the current setting)
        System.out.println("\n--- Step 5: Update Member Preference ---");
        Boolean memberNewReceiveSetting = !memberCurrentReceiveSetting;
        
        Map<String, Object> memberUpdateRequest = new HashMap<>();
        memberUpdateRequest.put("familyId", selectedFamilyId);
        memberUpdateRequest.put("memberUserId", otherMemberId);
        memberUpdateRequest.put("receiveMessages", memberNewReceiveSetting);
        
        ResponseEntity<Map<String, Object>> memberUpdateResponse = 
                memberMessagePreferencesController.updateMemberMessagePreferences(
                        testUser1.getId(), "test-token", memberUpdateRequest, null);
                        
        Map<String, Object> updatedMemberPref = memberUpdateResponse.getBody();
        if (updatedMemberPref == null) {
            System.out.println("No response from member preference update!");
            return;
        }
        
        System.out.println("Member preference updated: " + updatedMemberPref);
        System.out.println("New receive setting: " + updatedMemberPref.get("receiveMessages") + 
                          " (was: " + memberCurrentReceiveSetting + ")");
        
        // 6. Verify the update was saved in the database
        try {
            boolean verifiedSetting = jdbcTemplate.queryForObject(
                    "SELECT receive_messages FROM user_member_message_settings " +
                    "WHERE user_id = ? AND family_id = ? AND member_user_id = ?",
                    Boolean.class, testUser1.getId(), selectedFamilyId, otherMemberId);
                    
            System.out.println("\n--- Step 6: Verify Database Update ---");
            System.out.println("Database setting: " + verifiedSetting + 
                              " (expected: " + memberNewReceiveSetting + ")");
            System.out.println("Update verification: " + 
                              (verifiedSetting == memberNewReceiveSetting ? "SUCCESS" : "FAILED"));
        } catch (Exception e) {
            System.out.println("\n--- Step 6: Verify Database Update ---");
            System.out.println("Error querying database: " + e.getMessage());
        }
        
        // 7. Reset the settings back to their original values
        System.out.println("\n--- Step 7: Reset Settings to Original Values ---");
        
        // Reset family preference
        Map<String, Object> resetFamilyRequest = new HashMap<>();
        resetFamilyRequest.put("familyId", selectedFamilyId);
        resetFamilyRequest.put("receiveMessages", currentReceiveSetting);
        
        messagePreferencesController.updateMessagePreferences(
                testUser1.getId(), "test-token", resetFamilyRequest, null);
                
        // Reset member preference
        Map<String, Object> resetMemberRequest = new HashMap<>();
        resetMemberRequest.put("familyId", selectedFamilyId);
        resetMemberRequest.put("memberUserId", otherMemberId);
        resetMemberRequest.put("receiveMessages", memberCurrentReceiveSetting);
        
        memberMessagePreferencesController.updateMemberMessagePreferences(
                testUser1.getId(), "test-token", resetMemberRequest, null);
                
        System.out.println("Settings reset to original values.");
    }
} 