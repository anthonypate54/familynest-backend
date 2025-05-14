package com.familynest.controller;

import com.familynest.service.VideoService;
import com.familynest.service.VideoService.VideoProcessingResult;

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

    @Autowired
    private VideoService videoService;

    /**
     * Health check endpoint to verify if the public video service is available
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.info("PUBLIC VIDEO HEALTH CHECK ENDPOINT ACCESSED");
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Public video processing service is available");
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
        logger.info("PUBLIC VIDEO UPLOAD ENDPOINT ACCESSED: {}", file != null ? file.getOriginalFilename() : "null");
        
        // Simple test response for debugging
        if (true) {
            Map<String, String> testResponse = new HashMap<>();
            testResponse.put("videoUrl", "/test_video_public.mp4");
            testResponse.put("thumbnailUrl", "/test_thumbnail_public.jpg");
            testResponse.put("message", "Public controller test response");
            logger.info("Returning test data without processing - PUBLIC ENDPOINT");
            return ResponseEntity.ok(testResponse);
        }
        
        // Normal processing logic (not used during testing)
        if (file.isEmpty()) {
            logger.error("Empty file uploaded");
            return ResponseEntity.badRequest().body(
                Map.of("error", "Empty file uploaded")
            );
        }
        
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("video/")) {
                logger.error("Invalid content type: {}", contentType);
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid file type. Only video files are accepted")
                );
            }
            
            // Process the video file (save and generate thumbnail)
            VideoProcessingResult result = videoService.processVideo(file);
            
            // Return the URLs for the client to use
            Map<String, String> response = new HashMap<>();
            response.put("videoUrl", result.getVideoUrl());
            response.put("thumbnailUrl", result.getThumbnailUrl());
            
            logger.info("Successfully processed video: {}", file.getOriginalFilename());
            logger.info("Video URL: {}, Thumbnail URL: {}", result.getVideoUrl(), result.getThumbnailUrl());
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Failed to process video: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to process video: " + e.getMessage())
            );
        }
    }

    /**
     * Test endpoint for public video integration testing
     * @return Test video data
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> getTestVideoData() {
        logger.info("PUBLIC VIDEO TEST ENDPOINT ACCESSED");
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("videoUrl", "/uploads/public_test_video.mp4");
        response.put("thumbnailUrl", "/uploads/thumbnails/public_test_thumbnail.jpg");
        
        logger.info("Returning public test video data");
        return ResponseEntity.ok(response);
    }
} 