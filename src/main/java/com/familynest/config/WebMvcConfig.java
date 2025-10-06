package com.familynest.config;

import com.familynest.service.storage.LocalStorageService;
import com.familynest.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebMvcConfig.class);

    @Autowired
    private StorageService storageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only configure local file serving if we're using LocalStorageService
        if (storageService instanceof LocalStorageService) {
            LocalStorageService localStorageService = (LocalStorageService) storageService;
            Path uploadPath = localStorageService.getAbsolutePath("/");
            String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
            
            // Make sure there's a trailing slash for the file: URL
            if (!uploadAbsolutePath.endsWith("/")) {
                uploadAbsolutePath = uploadAbsolutePath + "/";
            }
            
            // Configure upload directory to serve all /uploads/** requests
            logger.info("Configuring local file serving for uploads: {}", uploadAbsolutePath);
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations("file:" + uploadAbsolutePath);
        } else {
            logger.info("Using remote storage service, skipping local file serving configuration");
        }
    }
} 
