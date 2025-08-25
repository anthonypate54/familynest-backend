package com.familynest.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuration for application caching
 * Uses in-memory caching for all environments for consistency
 * Redis can be added later as an additional layer
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        // Define all cache names used across the application
        // Add new caches here as they're introduced to avoid deployment issues
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "userMessages",
            "familyMessages", 
            "messageEngagement",
            "userFamilies",
            "userProfile",
            "messagePreferences",
            "invitations",
            "notifications",
            "userSettings",
            "familySettings"
        ));
        return cacheManager;
    }
} 