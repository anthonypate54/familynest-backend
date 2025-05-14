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
@Configuration
public class StorageServiceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageServiceConfig.class);
    
    @Value("${storage.type:local}")
    private String storageType;
    
    /**
     * Creates the primary StorageService bean based on configuration.
     */
    @Bean
    @Primary
    public StorageService storageService(
            @Autowired(required = false) LocalStorageService localStorageService,
            @Autowired(required = false) S3StorageService s3StorageService) {
        
        logger.info("Configuring storage service of type: {}", storageType);
        
        // Always log what we have available
        logger.info("Available storage services: localStorageService={}, s3StorageService={}", 
                 localStorageService != null ? "available" : "unavailable",
                 s3StorageService != null ? "available" : "unavailable");
        
        // For safety, always return a storage service
        if ("s3".equalsIgnoreCase(storageType) && s3StorageService != null) {
            logger.info("Using S3 storage service");
            return s3StorageService;
        } else {
            // Default to local
            if (localStorageService != null) {
                logger.info("Using local filesystem storage service");
                return localStorageService;
            } else {
                // This should never happen in proper configuration
                throw new IllegalStateException("No storage service implementation available! Verify your Spring profiles.");
            }
        }
    }
} 