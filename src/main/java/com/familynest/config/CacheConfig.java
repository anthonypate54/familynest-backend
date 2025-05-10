package com.familynest.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for application caching
 * Uses in-memory caching for development/testing
 * Should be replaced with Redis or similar in production
 * 
 * This configuration is not active when running with testdb profile
 */
@Configuration
@EnableCaching
@Profile("!testdb") // Only activate when NOT using testdb profile
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "userMessages",
            "familyMessages",
            "messageEngagement",
            "userFamilies",
            "userProfile"
        ));
        return cacheManager;
    }
} 