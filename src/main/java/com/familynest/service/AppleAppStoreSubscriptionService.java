package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Apple App Store implementation of the SubscriptionService
 * This is a placeholder implementation that will be completed when iOS support is added
 */
@Service
public class AppleAppStoreSubscriptionService extends SubscriptionService {
    
    @Autowired
    public AppleAppStoreSubscriptionService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
    
    @Override
    protected SubscriptionDetails getSubscriptionDetails(String transactionId, String productId) {
        // Placeholder implementation
        logger.warn("⚠️ Apple App Store subscription verification not yet implemented");
        
        // Return placeholder details
        return new SubscriptionDetails(
            -1.0,           // price
            "USD",          // currency
            false,          // isTrial
            null,           // linkedPurchaseToken
            null,           // offerId
            "UNIMPLEMENTED", // subscriptionState
            null            // startTime
        );
    }
    
    @Override
    protected String getPlatformName() {
        return "APPLE";
    }
}
