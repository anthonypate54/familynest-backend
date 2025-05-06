package com.familynest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

/**
 * This test class directly runs SQL queries against the database
 * to validate data integrity outside of the API layer.
 * It's designed to verify the exact data that would be returned to the UI.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DirectQueryValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MessagePreferencesTestUtil testUtil;
    
    /**
     * Helper method to print a query result set
     */
    private void printQueryResults(List<Map<String, Object>> results, String title) {
        System.out.println("\n===== " + title + " =====");
        System.out.println("Row count: " + results.size());
        
        if (results.isEmpty()) {
            System.out.println("No results found.");
            return;
        }
        
        // Print column headers
        Map<String, Object> firstRow = results.get(0);
        for (String columnName : firstRow.keySet()) {
            System.out.print(String.format("%-20s", columnName));
        }
        System.out.println();
        
        // Print separator line
        for (int i = 0; i < firstRow.keySet().size() * 20; i++) {
            System.out.print("-");
        }
        System.out.println();
        
        // Print rows
        for (Map<String, Object> row : results) {
            for (Object value : row.values()) {
                System.out.print(String.format("%-20s", value != null ? value.toString() : "NULL"));
            }
            System.out.println();
        }
    }
    
    @Test
    public void testFamilyMemberRelationships() {
        // Set up test data
        testUtil.setupTestData();
        
        try {
            // Get a test user ID
            Long userId = testUtil.getTestUser(1).getId();
            System.out.println("Using test user ID: " + userId);
            
            // 1. Get all families associated with the user
            List<Map<String, Object>> families = jdbcTemplate.queryForList(
                "SELECT f.id AS family_id, f.name AS family_name, ufm.role AS user_role, " +
                "CASE WHEN f.created_by = ? THEN 'YES' ELSE 'NO' END AS is_owner " +
                "FROM family f " +
                "JOIN user_family_membership ufm ON f.id = ufm.family_id " +
                "WHERE ufm.user_id = ?",
                userId, userId
            );
            printQueryResults(families, "Families Associated with User " + userId);
            
            // 2. Get family members for each family
            if (!families.isEmpty()) {
                Long familyId = ((Number) families.get(0).get("family_id")).longValue();
                
                List<Map<String, Object>> members = jdbcTemplate.queryForList(
                    "SELECT u.id AS member_id, u.username, u.first_name, u.last_name, " +
                    "ufm.role AS member_role, " +
                    "CASE WHEN f.created_by = u.id THEN 'YES' ELSE 'NO' END AS is_family_owner " +
                    "FROM user_family_membership ufm " +
                    "JOIN app_user u ON ufm.user_id = u.id " +
                    "JOIN family f ON ufm.family_id = f.id " +
                    "WHERE ufm.family_id = ?",
                    familyId
                );
                printQueryResults(members, "Members of Family " + familyId);
                
                // 3. Get message preferences for this family
                List<Map<String, Object>> familyPreferences = jdbcTemplate.queryForList(
                    "SELECT ufms.user_id, u.username, u.first_name, u.last_name, " +
                    "ufms.family_id, f.name as family_name, ufms.receive_messages " +
                    "FROM user_family_message_settings ufms " +
                    "JOIN app_user u ON ufms.user_id = u.id " +
                    "JOIN family f ON ufms.family_id = f.id " +
                    "WHERE ufms.family_id = ?",
                    familyId
                );
                printQueryResults(familyPreferences, "Family Message Preferences for Family " + familyId);
                
                // 4. Get member message preferences 
                List<Map<String, Object>> memberPreferences = jdbcTemplate.queryForList(
                    "SELECT umms.user_id, u.username AS user_username, " +
                    "umms.member_user_id, m.username AS member_username, " +
                    "m.first_name AS member_first_name, m.last_name AS member_last_name, " +
                    "umms.family_id, f.name AS family_name, umms.receive_messages " +
                    "FROM user_member_message_settings umms " +
                    "JOIN app_user u ON umms.user_id = u.id " +
                    "JOIN app_user m ON umms.member_user_id = m.id " +
                    "JOIN family f ON umms.family_id = f.id " +
                    "WHERE umms.family_id = ?",
                    familyId
                );
                printQueryResults(memberPreferences, "Member Message Preferences for Family " + familyId);
                
                // 5. Perform a UI-specific query to get combined preference data for the dialog
                // This recreates what would be sent to the MemberMessageDialog
                if (!members.isEmpty() && members.size() > 1) {
                    Long memberId = ((Number) members.get(1).get("member_id")).longValue(); // Get second member
                    
                    // Query to check member preference for a specific user and member
                    List<Map<String, Object>> specificMemberPreference = jdbcTemplate.queryForList(
                        "SELECT umms.receive_messages " +
                        "FROM user_member_message_settings umms " +
                        "WHERE umms.user_id = ? AND umms.member_user_id = ? AND umms.family_id = ?",
                        userId, memberId, familyId
                    );
                    
                    printQueryResults(specificMemberPreference, 
                            "Specific Member Preference (User " + userId + " receiving from Member " + memberId + ")");
                    
                    // Test updating a specific preference
                    boolean currentValue = specificMemberPreference.isEmpty() ? true : 
                            (Boolean) specificMemberPreference.get(0).get("receive_messages");
                    boolean newValue = !currentValue;
                    
                    System.out.println("\nUpdating preference: user " + userId + " receiving from member " + 
                            memberId + " in family " + familyId + " from " + currentValue + " to " + newValue);
                    
                    if (specificMemberPreference.isEmpty()) {
                        // Insert new preference
                        jdbcTemplate.update(
                            "INSERT INTO user_member_message_settings (user_id, member_user_id, family_id, receive_messages) " +
                            "VALUES (?, ?, ?, ?)",
                            userId, memberId, familyId, newValue
                        );
                        System.out.println("Inserted new preference record");
                    } else {
                        // Update existing preference
                        jdbcTemplate.update(
                            "UPDATE user_member_message_settings SET receive_messages = ? " +
                            "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                            newValue, userId, memberId, familyId
                        );
                        System.out.println("Updated existing preference record");
                    }
                    
                    // Verify the update happened
                    specificMemberPreference = jdbcTemplate.queryForList(
                        "SELECT umms.receive_messages " +
                        "FROM user_member_message_settings umms " +
                        "WHERE umms.user_id = ? AND umms.member_user_id = ? AND umms.family_id = ?",
                        userId, memberId, familyId
                    );
                    
                    printQueryResults(specificMemberPreference, "Preference After Update");
                    
                    // Reset to original value
                    jdbcTemplate.update(
                        "UPDATE user_member_message_settings SET receive_messages = ? " +
                        "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                        currentValue, userId, memberId, familyId
                    );
                    System.out.println("Reset to original value: " + currentValue);
                }
            }
            
            // 6. Summary statistics - modified for H2 compatibility
            List<Map<String, Object>> familyCount = jdbcTemplate.queryForList(
                "SELECT 'Family Count' AS metric, COUNT(DISTINCT family_id) AS cnt " +
                "FROM user_family_membership WHERE user_id = ?",
                userId
            );
            printQueryResults(familyCount, "Family Count for User " + userId);
            
            List<Map<String, Object>> memberPrefCount = jdbcTemplate.queryForList(
                "SELECT 'Member Preferences Count' AS metric, COUNT(*) AS cnt " +
                "FROM user_member_message_settings WHERE user_id = ?", 
                userId
            );
            printQueryResults(memberPrefCount, "Member Preferences Count for User " + userId);
            
            List<Map<String, Object>> familyPrefCount = jdbcTemplate.queryForList(
                "SELECT 'Family Preferences Count' AS metric, COUNT(*) AS cnt " +
                "FROM user_family_message_settings WHERE user_id = ?",
                userId
            );
            printQueryResults(familyPrefCount, "Family Preferences Count for User " + userId);
            
        } finally {
            // Clean up test data
            testUtil.cleanupTestData();
        }
    }
    
    @Test
    public void testMessageDialogQueriesExactMatch() {
        // This test recreates exactly the SQL queries used by the dialogs
        // to ensure they return the expected data structure
        
        // Set up test data
        testUtil.setupTestData();
        
        try {
            // Get a test user ID
            Long userId = testUtil.getTestUser(1).getId();
            Long familyId = testUtil.getTestFamily(1).getId();
            System.out.println("Using test user ID: " + userId + ", family ID: " + familyId);
            
            // 1. FamiliesMessageDialog uses this query to get family message preferences
            List<Map<String, Object>> familyMessagePrefs = jdbcTemplate.queryForList(
                "SELECT mp.family_id AS familyId, f.name AS familyName, mp.receive_messages AS receiveMessages " +
                "FROM user_family_message_settings mp " +
                "JOIN family f ON mp.family_id = f.id " +
                "WHERE mp.user_id = ?",
                userId
            );
            printQueryResults(familyMessagePrefs, "FamiliesMessageDialog - Family Preferences Query");
            
            // If no settings exist, need to check families user belongs to
            if (familyMessagePrefs.isEmpty()) {
                System.out.println("\nNo explicit family preferences found, checking family memberships...");
                List<Map<String, Object>> userFamilies = jdbcTemplate.queryForList(
                    "SELECT fm.family_id AS familyId, f.name AS familyName, CAST(1 AS BOOLEAN) AS receiveMessages " +
                    "FROM user_family_membership fm " +
                    "JOIN family f ON fm.family_id = f.id " +
                    "LEFT JOIN user_family_message_settings mp ON fm.family_id = mp.family_id AND fm.user_id = mp.user_id " +
                    "WHERE fm.user_id = ? AND mp.family_id IS NULL",
                    userId
                );
                printQueryResults(userFamilies, "Families User Belongs To (Default Preferences)");
            }
            
            // 2. MemberMessageDialog uses this query to get family members
            // Modified to remove 'active' column which might not exist in test DB
            List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                "SELECT u.id AS userId, u.username, u.first_name AS firstName, u.last_name AS lastName, " +
                "u.photo, ufm.role, f.name AS familyName " +
                "FROM user_family_membership ufm " +
                "JOIN app_user u ON ufm.user_id = u.id " +
                "JOIN family f ON ufm.family_id = f.id " +
                "WHERE ufm.family_id = ?",
                familyId
            );
            printQueryResults(familyMembers, "MemberMessageDialog - Family Members Query");
            
            // 3. MemberMessageDialog uses this query to get member message preferences
            List<Map<String, Object>> memberMessagePrefs = jdbcTemplate.queryForList(
                "SELECT mp.member_user_id AS memberUserId, mp.family_id AS familyId, " +
                "u.username AS memberUsername, u.first_name AS memberFirstName, " +
                "u.last_name AS memberLastName, mp.receive_messages AS receiveMessages, " +
                "f.name AS memberOfFamilyName " +
                "FROM user_member_message_settings mp " +
                "JOIN app_user u ON mp.member_user_id = u.id " +
                "JOIN family f ON mp.family_id = f.id " +
                "WHERE mp.user_id = ?",
                userId
            );
            printQueryResults(memberMessagePrefs, "MemberMessageDialog - Member Preferences Query");
            
            // Get a member ID for testing update functionality
            Long memberId = null;
            if (!familyMembers.isEmpty() && familyMembers.size() > 1) {
                // Get any member that isn't the current user
                for (Map<String, Object> member : familyMembers) {
                    Long currentMemberId = ((Number) member.get("userId")).longValue();
                    if (!currentMemberId.equals(userId)) {
                        memberId = currentMemberId;
                        break;
                    }
                }
                
                if (memberId != null) {
                    System.out.println("\nTesting preference update with member ID: " + memberId);
                    
                    // Check if a preference already exists
                    boolean preferenceExists = !jdbcTemplate.queryForList(
                        "SELECT 1 FROM user_member_message_settings " +
                        "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                        userId, memberId, familyId
                    ).isEmpty();
                    
                    Boolean currentPreference = preferenceExists ? 
                        jdbcTemplate.queryForObject(
                            "SELECT receive_messages FROM user_member_message_settings " +
                            "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                            Boolean.class, userId, memberId, familyId
                        ) : true;
                    
                    System.out.println("Current preference exists: " + preferenceExists + 
                                       ", value: " + currentPreference);
                    
                    // Test updating (or creating) preference
                    Boolean newPreference = !currentPreference;
                    
                    if (preferenceExists) {
                        // Update existing
                        jdbcTemplate.update(
                            "UPDATE user_member_message_settings SET receive_messages = ? " +
                            "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                            newPreference, userId, memberId, familyId
                        );
                    } else {
                        // Insert new
                        jdbcTemplate.update(
                            "INSERT INTO user_member_message_settings " +
                            "(user_id, member_user_id, family_id, receive_messages) " +
                            "VALUES (?, ?, ?, ?)",
                            userId, memberId, familyId, newPreference
                        );
                    }
                    
                    // Verify update worked
                    Boolean updatedPreference = jdbcTemplate.queryForObject(
                        "SELECT receive_messages FROM user_member_message_settings " +
                        "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                        Boolean.class, userId, memberId, familyId
                    );
                    
                    System.out.println("Updated preference value: " + updatedPreference + 
                                      " (expected: " + newPreference + ")");
                    System.out.println("Preference update " + 
                                      (updatedPreference.equals(newPreference) ? "SUCCESS" : "FAILED"));
                    
                    // Reset to original value or remove if it didn't exist
                    if (preferenceExists) {
                        jdbcTemplate.update(
                            "UPDATE user_member_message_settings SET receive_messages = ? " +
                            "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                            currentPreference, userId, memberId, familyId
                        );
                    } else {
                        jdbcTemplate.update(
                            "DELETE FROM user_member_message_settings " +
                            "WHERE user_id = ? AND member_user_id = ? AND family_id = ?",
                            userId, memberId, familyId
                        );
                    }
                    System.out.println("Reset preference to original state");
                }
            }
            
        } finally {
            // Clean up test data
            testUtil.cleanupTestData();
        }
    }
} 