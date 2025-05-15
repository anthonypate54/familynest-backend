package com.familynest.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Configuration class that selects the appropriate StorageService
 * based on the configuration or environment
 */
// Temporarily commented out to fix authentication issues
// @Configuration
public class StorageServiceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageServiceConfig.class);
    
    @Value("${storage.type:local}")
    private String storageType;
    
    @Autowired
    private Environment environment;
    
    // @Bean
    // @Primary
    public StorageService storageService(
            LocalStorageService localStorageService,
            @Autowired(required = false) S3StorageService s3StorageService) {
        
        logger.info("Configuring storage service of type: {}", storageType);
        
        // Check active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        logger.info("Active profiles: {}", String.join(", ", activeProfiles));
        
        switch (storageType.toLowerCase()) {
            case "s3":
                logger.info("Using S3 storage service");
                if (s3StorageService != null) {
                    return s3StorageService;
                } else {
                    logger.warn("S3 storage service requested but not available, falling back to local");
                    return localStorageService;
                }
            case "local":
            default:
                logger.info("Using local filesystem storage service");
                return localStorageService;
        }
    }
} 