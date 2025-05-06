package com.familynest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility for loading a large test dataset for comprehensive testing
 * of the member message and family message preferences.
 */
@Component
public class LargeDatasetLoader {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Loads the large test dataset into the database.
     * Should only be called in test environments.
     */
    public void loadLargeTestData() {
        try {
            ClassPathResource resource = new ClassPathResource("test-data-large.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            // Split SQL by semicolons to execute each statement separately
            String[] statements = sql.split(";");
            
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    jdbcTemplate.execute(statement.trim());
                }
            }
            
            System.out.println("Successfully loaded large test dataset with complex family structures");
        } catch (IOException e) {
            System.err.println("Failed to load large test dataset: " + e.getMessage());
        }
    }
    
    /**
     * Clears all test data from the database.
     * Should only be called in test environments.
     */
    public void cleanUpTestData() {
        try {
            // Delete in reverse order of dependencies
            jdbcTemplate.execute("DELETE FROM user_member_message_settings");
            jdbcTemplate.execute("DELETE FROM user_family_message_settings");
            jdbcTemplate.execute("DELETE FROM user_family_membership");
            jdbcTemplate.execute("DELETE FROM family");
            jdbcTemplate.execute("DELETE FROM app_user");
            
            System.out.println("Successfully cleaned up large test dataset");
        } catch (Exception e) {
            System.err.println("Failed to clean up test data: " + e.getMessage());
        }
    }
    
    /**
     * Utility method to get query counts to verify data was loaded correctly
     */
    public void printDatasetStats() {
        int userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
        int familyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM family", Integer.class);
        int membershipCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_family_membership", Integer.class);
        int familyPrefCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_family_message_settings", Integer.class);
        int memberPrefCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_member_message_settings", Integer.class);
        
        System.out.println("\n===== Test Dataset Statistics =====");
        System.out.println("Users: " + userCount);
        System.out.println("Families: " + familyCount);
        System.out.println("Family Memberships: " + membershipCount);
        System.out.println("Family Message Preferences: " + familyPrefCount);
        System.out.println("Member Message Preferences: " + memberPrefCount);
        
        // Print user with most families
        jdbcTemplate.query(
            "SELECT u.username, COUNT(DISTINCT fm.family_id) as family_count " +
            "FROM app_user u " +
            "JOIN user_family_membership fm ON u.id = fm.user_id " +
            "GROUP BY u.id, u.username " +
            "ORDER BY family_count DESC " +
            "LIMIT 3",
            (rs) -> {
                System.out.println("User " + rs.getString("username") + 
                                  " belongs to " + rs.getInt("family_count") + " families");
            }
        );
        
        // Print family with most members
        jdbcTemplate.query(
            "SELECT f.name, COUNT(DISTINCT fm.user_id) as member_count " +
            "FROM family f " +
            "JOIN user_family_membership fm ON f.id = fm.family_id " +
            "GROUP BY f.id, f.name " +
            "ORDER BY member_count DESC " +
            "LIMIT 3",
            (rs) -> {
                System.out.println("Family " + rs.getString("name") + 
                                  " has " + rs.getInt("member_count") + " members");
            }
        );
    }
} 