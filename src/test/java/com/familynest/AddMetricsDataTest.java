package com.familynest;

import com.familynest.service.EngagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test class to add sample metrics data to a test message
 */
@SpringBootTest
@ActiveProfiles("testdb")
public class AddMetricsDataTest {

    @Autowired
    private EngagementService engagementService;
    
    private static final Long TEST_MESSAGE_ID = 1L;
    
    /**
     * Add sample metrics data to a test message
     */
    @Test
    public void addSampleMetricsData() {
        // Add views from different users
        engagementService.markMessageAsViewed(TEST_MESSAGE_ID, 101L);
        engagementService.markMessageAsViewed(TEST_MESSAGE_ID, 102L);
        engagementService.markMessageAsViewed(TEST_MESSAGE_ID, 103L);
        
        // Add reactions from different users
        engagementService.addReaction(TEST_MESSAGE_ID, 101L, "LIKE");
        engagementService.addReaction(TEST_MESSAGE_ID, 102L, "LOVE");
        engagementService.addReaction(TEST_MESSAGE_ID, 103L, "LIKE");
        engagementService.addReaction(TEST_MESSAGE_ID, 104L, "HAHA");
        
        // Add comments
        engagementService.addComment(TEST_MESSAGE_ID, 101L, "This is a great message!", null);
        engagementService.addComment(TEST_MESSAGE_ID, 102L, "I agree with this completely!", null);
        
        // Print the final metrics
        System.out.println("Metrics added to message ID " + TEST_MESSAGE_ID);
        System.out.println("Engagement data: " + engagementService.getMessageEngagementData(TEST_MESSAGE_ID));
    }
} 