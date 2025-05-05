package com.familynest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Utility class for loading test data into the database
 */
@Component
public class TestDataLoader {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public TestDataLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Loads test data from the populate_test_data.sql script
     * This should be called in a test context only
     */
    @Transactional
    public void loadTestData() {
        try {
            String script = readScriptFile("scripts/populate_test_data.sql");
            System.out.println("Executing test data population script...");
            
            // Execute the script
            jdbcTemplate.execute(script);
            
            System.out.println("Test data loaded successfully");
        } catch (IOException e) {
            System.err.println("Failed to load test data script: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Cleans test data from the database
     * This should be called after tests are completed
     */
    @Transactional
    public void cleanTestData() {
        // Remove test users and all associated data
        System.out.println("Cleaning test data...");
        
        // Delete messages from test users
        jdbcTemplate.update("DELETE FROM message WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Delete member message settings for test users
        jdbcTemplate.update("DELETE FROM user_member_message_settings WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        jdbcTemplate.update("DELETE FROM user_member_message_settings WHERE member_user_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Delete family message settings for test users
        jdbcTemplate.update("DELETE FROM user_family_message_settings WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Delete family memberships for test users
        jdbcTemplate.update("DELETE FROM user_family_membership WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Delete invitations related to test users
        jdbcTemplate.update("DELETE FROM invitation WHERE sender_id IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Delete families created by test users
        jdbcTemplate.update("DELETE FROM family WHERE created_by IN (SELECT id FROM app_user WHERE username LIKE 'testuser%')");
        
        // Finally, delete the test users
        jdbcTemplate.update("DELETE FROM app_user WHERE username LIKE 'testuser%'");
        
        System.out.println("Test data cleaned successfully");
    }
    
    /**
     * Read a script file from the classpath
     */
    private String readScriptFile(String scriptPath) throws IOException {
        ClassPathResource resource = new ClassPathResource(scriptPath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
} 