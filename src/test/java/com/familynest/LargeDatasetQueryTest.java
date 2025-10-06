package com.familynest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class LargeDatasetQueryTest {

    @Autowired
    private TestLargeDatasetLoader datasetLoader;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @BeforeAll
    public void setUp() {
        datasetLoader.loadLargeTestData();
        datasetLoader.printDatasetStats();
    }
    
    @AfterAll
    public void cleanUp() {
        datasetLoader.cleanUpTestData();
    }
    
    /**
     * Helper method to print query results in a nicely formatted table
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
    public void testFamilyMessagePreferencesQueries() {
        // Use John Doe (id 101) as the test user
        final Long userId = 101L;
        
        System.out.println("\n========= TESTING FAMILY MESSAGE PREFERENCES QUERIES =========");
        
        // 1. Query to get family message preferences for a user
        List<Map<String, Object>> familyPrefs = jdbcTemplate.queryForList(
            "SELECT mp.family_id AS familyId, f.name AS familyName, mp.receive_messages AS receiveMessages " +
            "FROM user_family_message_settings mp " +
            "JOIN family f ON mp.family_id = f.id " +
            "WHERE mp.user_id = ?",
            userId
        );
        printQueryResults(familyPrefs, "John Doe's Family Preferences");
        
        // 2. Get families with implicit default preferences
        List<Map<String, Object>> implicitPrefs = jdbcTemplate.queryForList(
            "SELECT fm.family_id AS familyId, f.name AS familyName, CAST(1 AS BOOLEAN) AS receiveMessages " +
            "FROM user_family_membership fm " +
            "JOIN family f ON fm.family_id = f.id " +
            "LEFT JOIN user_family_message_settings mp ON fm.family_id = mp.family_id AND fm.user_id = mp.user_id " +
            "WHERE fm.user_id = ? AND mp.family_id IS NULL",
            userId
        );
        printQueryResults(implicitPrefs, "John Doe's Implicit Default Preferences");
        
        // 3. Combined query to get all family preferences (explicit + implicit)
        List<Map<String, Object>> allPrefs = jdbcTemplate.queryForList(
            "SELECT f.id AS familyId, f.name AS familyName, " +
            "CASE WHEN mp.receive_messages IS NULL THEN CAST(1 AS BOOLEAN) ELSE mp.receive_messages END AS receiveMessages, " +
            "CASE WHEN mp.receive_messages IS NULL THEN 'DEFAULT' ELSE 'EXPLICIT' END AS preferenceType " +
            "FROM user_family_membership fm " +
            "JOIN family f ON fm.family_id = f.id " +
            "LEFT JOIN user_family_message_settings mp ON fm.family_id = mp.family_id AND fm.user_id = mp.user_id " +
            "WHERE fm.user_id = ?",
            userId
        );
        printQueryResults(allPrefs, "John Doe's All Family Preferences (Explicit + Implicit Defaults)");
    }
    
    @Test
    public void testMemberMessagePreferencesQueries() {
        // Use John Doe (id 101) for testing
        final Long userId = 101L;
        final Long familyId = 201L; // The Doe Family
        
        System.out.println("\n========= TESTING MEMBER MESSAGE PREFERENCES QUERIES =========");
        
        // 1. Query to get all members of a family
        List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
            "SELECT u.id AS userId, u.username, u.first_name AS firstName, u.last_name AS lastName, " +
            "ufm.role, f.name AS familyName " +
            "FROM user_family_membership ufm " +
            "JOIN app_user u ON ufm.user_id = u.id " +
            "JOIN family f ON ufm.family_id = f.id " +
            "WHERE ufm.family_id = ?",
            familyId
        );
        printQueryResults(familyMembers, "Members of The Doe Family");
        
        // 2. Query to get member message preferences
        List<Map<String, Object>> memberPrefs = jdbcTemplate.queryForList(
            "SELECT mp.member_user_id AS memberUserId, mp.family_id AS familyId, " +
            "u.username AS memberUsername, u.first_name AS memberFirstName, " +
            "u.last_name AS memberLastName, mp.receive_messages AS receiveMessages, " +
            "f.name AS memberOfFamilyName " +
            "FROM user_member_message_settings mp " +
            "JOIN app_user u ON mp.member_user_id = u.id " +
            "JOIN family f ON mp.family_id = f.id " +
            "WHERE mp.user_id = ? AND mp.family_id = ?",
            userId, familyId
        );
        printQueryResults(memberPrefs, "John Doe's Member Preferences in The Doe Family");
        
        // 3. Combined query to show all member preferences (explicit + implicit defaults)
        List<Map<String, Object>> allMemberPrefs = jdbcTemplate.queryForList(
            "SELECT m.id AS memberId, m.username, m.first_name AS firstName, m.last_name AS lastName, " +
            "CASE WHEN mp.receive_messages IS NULL THEN CAST(1 AS BOOLEAN) ELSE mp.receive_messages END AS receiveMessages, " +
            "CASE WHEN mp.receive_messages IS NULL THEN 'DEFAULT' ELSE 'EXPLICIT' END AS preferenceType " +
            "FROM user_family_membership fm " +
            "JOIN family f ON fm.family_id = f.id " +
            "JOIN user_family_membership mfm ON f.id = mfm.family_id " +
            "JOIN app_user m ON mfm.user_id = m.id " +
            "LEFT JOIN user_member_message_settings mp ON fm.user_id = mp.user_id AND mp.member_user_id = m.id AND mp.family_id = f.id " +
            "WHERE fm.user_id = ? AND fm.family_id = ? AND m.id != ?",
            userId, familyId, userId
        );
        printQueryResults(allMemberPrefs, "John Doe's All Member Preferences (Explicit + Implicit Defaults)");
    }
    
    @Test
    public void testComplexQueries() {
        // Use Jane Doe (id 102) for testing complex scenarios
        final Long userId = 102L;
        
        System.out.println("\n========= TESTING COMPLEX QUERIES FOR UI SCENARIOS =========");
        
        // 1. For a given user, find all families they belong to and how many members are in each family
        List<Map<String, Object>> userFamiliesWithCounts = jdbcTemplate.queryForList(
            "SELECT f.id AS familyId, f.name AS familyName, " +
            "COUNT(DISTINCT mfm.user_id) AS memberCount, " +
            "SUM(CASE WHEN mp.receive_messages = true THEN 1 ELSE 0 END) AS receiveFromCount, " +
            "MAX(CASE WHEN f.created_by = ? THEN 'YES' ELSE 'NO' END) AS isOwner " +
            "FROM user_family_membership fm " +
            "JOIN family f ON fm.family_id = f.id " +
            "JOIN user_family_membership mfm ON f.id = mfm.family_id " +
            "LEFT JOIN user_member_message_settings mp ON fm.user_id = mp.user_id AND mp.member_user_id = mfm.user_id AND mp.family_id = f.id " +
            "WHERE fm.user_id = ? " +
            "GROUP BY f.id, f.name",
            userId, userId
        );
        printQueryResults(userFamiliesWithCounts, "Jane Doe's Families with Member Counts");
        
        // 2. For a given member in a family, get all the messaging preferences from other members
        final Long targetMemberId = 101L; // John Doe
        final Long targetFamilyId = 201L; // The Doe Family
        
        List<Map<String, Object>> memberMessagingMatrix = jdbcTemplate.queryForList(
            "SELECT u.id AS userId, u.username, u.first_name AS firstName, u.last_name AS lastName, " +
            "mp.receive_messages AS receivesFromJohn, " +
            "jmp.receive_messages AS johnReceivesFromThem " +
            "FROM user_family_membership fm " +
            "JOIN app_user u ON fm.user_id = u.id " +
            "LEFT JOIN user_member_message_settings mp ON u.id = mp.user_id AND mp.member_user_id = ? AND mp.family_id = ? " +
            "LEFT JOIN user_member_message_settings jmp ON ? = jmp.user_id AND jmp.member_user_id = u.id AND jmp.family_id = ? " +
            "WHERE fm.family_id = ? AND u.id != ? " +
            "ORDER BY u.username",
            targetMemberId, targetFamilyId, targetMemberId, targetFamilyId, targetFamilyId, targetMemberId
        );
        printQueryResults(memberMessagingMatrix, "Messaging Matrix for John Doe in The Doe Family");
        
        // 3. Find families where a user is a member but doesn't receive messages
        List<Map<String, Object>> mutedFamilies = jdbcTemplate.queryForList(
            "SELECT f.id AS familyId, f.name AS familyName, ufm.role, " +
            "CASE WHEN ufms.receive_messages IS NULL THEN 'DEFAULT (ON)' ELSE " +
            "CASE WHEN ufms.receive_messages = true THEN 'ON' ELSE 'MUTED' END END AS messageStatus " +
            "FROM user_family_membership ufm " +
            "JOIN family f ON ufm.family_id = f.id " +
            "LEFT JOIN user_family_message_settings ufms ON ufm.user_id = ufms.user_id AND ufm.family_id = ufms.family_id " +
            "WHERE ufm.user_id = ? AND (ufms.receive_messages = false OR ufms.receive_messages IS NULL) " +
            "ORDER BY f.name",
            userId
        );
        printQueryResults(mutedFamilies, "Families Where Jane Doe May Have Muted Messages");
        
        // 4. Find members that Jane Doe has explicitly muted
        List<Map<String, Object>> mutedMembers = jdbcTemplate.queryForList(
            "SELECT u.id AS memberId, u.username, u.first_name AS firstName, u.last_name AS lastName, " +
            "f.name AS familyName, mp.receive_messages " +
            "FROM user_member_message_settings mp " +
            "JOIN app_user u ON mp.member_user_id = u.id " +
            "JOIN family f ON mp.family_id = f.id " +
            "WHERE mp.user_id = ? AND mp.receive_messages = false",
            userId
        );
        printQueryResults(mutedMembers, "Members That Jane Doe Has Explicitly Muted");
    }
} 
