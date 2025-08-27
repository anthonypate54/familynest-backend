package com.familynest.controller;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Public video controller that handles video upload requests without requiring
 * authentication. This is a separate controller to ensure it's not affected by
 * any security issues with the main video controller.
 */
@RestController
@RequestMapping("/public/videos")
public class PublicVideoController {

    private static final Logger logger = LoggerFactory.getLogger(PublicVideoController.class);



    /**
     * Health check endpoint to verify if the public video service is available
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.info("PUBLIC VIDEO HEALTH CHECK ENDPOINT ACCESSED");
        Map<String, String> response = new HashMap<>();
        response.put("status", "DISABLED");
        response.put("message", "Public video processing service has been disabled for security reasons");
        return ResponseEntity.ok(response);
    }

    /**
     * Upload a video file and process it - public endpoint with no auth required
     * 
     * @param file The video file to upload
     * @return The URLs of the uploaded video and its thumbnail
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        logger.warn("DISABLED PUBLIC VIDEO UPLOAD ENDPOINT ACCESSED: {}", file != null ? file.getOriginalFilename() : "null");
        
        // Return a security error message
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "DISABLED");
        errorResponse.put("error", "This endpoint has been disabled for security reasons");
        errorResponse.put("message", "Please use the authenticated API endpoints instead");
        logger.info("Rejected public video upload attempt");
        
        return ResponseEntity.status(403).body(errorResponse);
    }

    /**
     * Test endpoint for public video integration testing
     * @return Test video data
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> getTestVideoData() {
        logger.info("PUBLIC VIDEO TEST ENDPOINT ACCESSED");
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "DISABLED");
        response.put("message", "Public video endpoints have been disabled for security reasons");
        
        logger.info("Returning disabled status for public test endpoint");
        return ResponseEntity.ok(response);
    }
} 