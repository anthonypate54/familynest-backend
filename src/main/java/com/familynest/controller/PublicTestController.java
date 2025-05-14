package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple public controller with no authentication requirements
 */
@RestController
@RequestMapping("/public") // Public path that should never be protected
@CrossOrigin(origins = "*")  // Allow from any origin
public class PublicTestController {

    private static final Logger logger = LoggerFactory.getLogger(PublicTestController.class);

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        logger.info("Public test endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "This is a public endpoint that should be accessible without authentication");
        
        // Add a test thumbnail URL
        response.put("testThumbnailUrl", "/uploads/thumbnails/test_thumbnail.jpg");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/video-test")
    public ResponseEntity<Map<String, Object>> videoTest() {
        logger.info("Public video test endpoint called");
        
        Map<String, Object> message = new HashMap<>();
        message.put("id", 12345);
        message.put("content", "Test video message");
        message.put("mediaType", "video");
        message.put("mediaUrl", "https://example.com/test-video.mp4");
        message.put("thumbnailUrl", "https://via.placeholder.com/320x180");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        
        return ResponseEntity.ok(response);
    }
} 