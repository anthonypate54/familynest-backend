package com.familynest.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for loading a large test dataset for comprehensive testing
 * of the member message and family message preferences.
 * This class is only active in test and dev profiles.
 */
@Component
@Profile({"test", "dev"})
public class LargeDatasetLoader {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Loads the standard test dataset into the database.
     * Should only be called in test environments.
     */
    public void loadLargeTestData() {
        loadSqlScript("test-data-large.sql");
    }
    
    /**
     * Loads a much larger dataset specifically for UI testing with scrolling lists.
     * This creates hundreds of users and family relationships.
     */
    public void loadUiTestData() {
        loadSqlScript("large-ui-dataset.sql");
    }
    
    /**
     * Helper method to load a SQL script from the classpath
     */
    private void loadSqlScript(String scriptPath) {
        try {
            ClassPathResource resource = new ClassPathResource(scriptPath);
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            System.out.println("Loading SQL script: " + scriptPath);
            
            // Find DO blocks and regular statements
            Pattern doBlockPattern = Pattern.compile("DO \\$\\$(.*?)\\$\\$;", Pattern.DOTALL);
            Matcher matcher = doBlockPattern.matcher(sql);
            
            int lastEnd = 0;
            while (matcher.find()) {
                // Execute any regular SQL statements before the DO block
                String regularStatements = sql.substring(lastEnd, matcher.start());
                executeStatements(regularStatements);
                
                // Execute the DO block as a whole
                String doBlock = matcher.group(0);
                System.out.println("Executing PostgreSQL DO block...");
                jdbcTemplate.execute(doBlock);
                
                lastEnd = matcher.end();
            }
            
            // Execute any remaining SQL statements after the last DO block
            if (lastEnd < sql.length()) {
                String remainingStatements = sql.substring(lastEnd);
                executeStatements(remainingStatements);
            }
            
            System.out.println("Successfully loaded dataset from " + scriptPath);
        } catch (IOException e) {
            System.err.println("Failed to load dataset from " + scriptPath + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error executing SQL: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to execute regular SQL statements (non-DO blocks)
     */
    private void executeStatements(String sql) {
        String[] statements = sql.split(";");
        for (String statement : statements) {
            if (!statement.trim().isEmpty()) {
                jdbcTemplate.execute(statement.trim());
            }
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
            
            System.out.println("Successfully cleaned up test dataset");
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
