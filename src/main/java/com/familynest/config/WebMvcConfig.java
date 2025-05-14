package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebMvcConfig.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    
    @Value("${app.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose upload directory through /uploads/** URL pattern
        Path uploadPath = Paths.get(uploadDir);
        String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
        
        // Make sure there's a trailing slash for the file: URL
        if (!uploadAbsolutePath.endsWith("/")) {
            uploadAbsolutePath = uploadAbsolutePath + "/";
        }
        
        // Configure upload directory
        logger.info("Configuring static resource handler for uploads: {}", uploadAbsolutePath);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadAbsolutePath);
        
        // Configure thumbnails directory
        Path thumbnailPath = Paths.get(thumbnailDir);
        String thumbnailAbsolutePath = thumbnailPath.toFile().getAbsolutePath();
        
        // Make sure there's a trailing slash for the file: URL
        if (!thumbnailAbsolutePath.endsWith("/")) {
            thumbnailAbsolutePath = thumbnailAbsolutePath + "/";
        }
        
        logger.info("Configuring static resource handler for thumbnails: {}", thumbnailAbsolutePath);
        registry.addResourceHandler("/uploads/thumbnails/**")
                .addResourceLocations("file:" + thumbnailAbsolutePath);
    }
} 