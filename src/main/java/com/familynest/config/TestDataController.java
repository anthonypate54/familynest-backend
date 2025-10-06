package com.familynest.config;

import com.familynest.util.LargeDatasetLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for loading test data in development/test environments.
 * This controller is only active in "test" or "dev" profiles.
 */
@RestController
@RequestMapping("/api/test")
@Profile({"test", "dev"})
public class TestDataController {

    @Autowired(required = false)
    private LargeDatasetLoader datasetLoader;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/load-data")
    public Map<String, String> loadTestData() {
        Map<String, String> response = new HashMap<>();
        
        if (datasetLoader == null) {
            response.put("status", "error");
            response.put("message", "DatasetLoader not available. Make sure LargeDatasetLoader is in classpath.");
            return response;
        }
        
        try {
            datasetLoader.cleanUpTestData(); // Clean first to avoid duplicates
            datasetLoader.loadLargeTestData();
            response.put("status", "success");
            response.put("message", "Test data loaded successfully");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error loading test data: " + e.getMessage());
        }
        
        return response;
    }
    
    @GetMapping("/load-ui-data")
    public Map<String, String> loadUiTestData() {
        Map<String, String> response = new HashMap<>();
        
        if (datasetLoader == null) {
            response.put("status", "error");
            response.put("message", "DatasetLoader not available. Make sure LargeDatasetLoader is in classpath.");
            return response;
        }
        
        try {
            datasetLoader.cleanUpTestData(); // Clean first to avoid duplicates
            datasetLoader.loadUiTestData();
            response.put("status", "success");
            response.put("message", "UI test data loaded successfully with 100+ users and 20+ families");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error loading UI test data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return response;
    }
    
    @GetMapping("/clear-data")
    public Map<String, String> clearTestData() {
        Map<String, String> response = new HashMap<>();
        
        if (datasetLoader == null) {
            response.put("status", "error");
            response.put("message", "DatasetLoader not available. Make sure LargeDatasetLoader is in classpath.");
            return response;
        }
        
        try {
            datasetLoader.cleanUpTestData();
            response.put("status", "success");
            response.put("message", "Test data cleared successfully");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error clearing test data: " + e.getMessage());
        }
        
        return response;
    }
    
    @GetMapping("/status")
    public Map<String, Object> getDataStatus() {
        Map<String, Object> response = new HashMap<>();
        
        if (datasetLoader == null) {
            response.put("status", "unavailable");
            response.put("message", "DatasetLoader not available");
            return response;
        }
        
        try {
            int userCount = countTable("app_user");
            int familyCount = countTable("family");
            int membershipCount = countTable("user_family_membership");
            int familyPrefCount = countTable("user_family_message_settings");
            int memberPrefCount = countTable("user_member_message_settings");
            
            response.put("status", "success");
            response.put("userCount", userCount);
            response.put("familyCount", familyCount);
            response.put("membershipCount", membershipCount);
            response.put("familyPrefCount", familyPrefCount);
            response.put("memberPrefCount", memberPrefCount);
            response.put("hasTestData", userCount > 0 && familyCount > 0);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error getting data status: " + e.getMessage());
        }
        
        return response;
    }
    
    private int countTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count != null ? count : 0;
    }
} 
