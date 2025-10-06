package com.familynest.controller;

import com.familynest.service.ThumbnailService;
import com.familynest.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for video-related endpoints with a simple implementation
 * that uses the ThumbnailService for generating thumbnails
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    private StorageService storageService;
    
    @Autowired
    private ThumbnailService thumbnailService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Health check endpoint to verify if the video service is available
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.info("VIDEO HEALTH CHECK ENDPOINT ACCESSED");
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Video processing service is available");
        return ResponseEntity.ok(response);
    }



    /**
     * Upload endpoint - saves the video and generates a thumbnail
     */
    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestPart("file") MultipartFile file) {
        logger.error("VIDEO UPLOAD ENDPOINT ACCESSED: {}", file.getOriginalFilename());
        
        try {
            // Create a unique filename
            String originalFilename = file.getOriginalFilename();
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            String filename = timestamp + "_" + uniqueId + "_" + 
                        (originalFilename != null ? originalFilename.replace(" ", "_") : "video.mp4");

            // Store video file using StorageService
            String storedPath = storageService.store(file, "videos", filename);
            logger.debug("Video saved at path: {}", storedPath);
            
            // Get the URL from StorageService
            String videoUrl = storageService.getUrl(storedPath);
            
            // Generate a thumbnail for the video
            String thumbnailUrl = getThumbnailForVideo(storedPath);
            logger.debug("Generated thumbnail URL: {}", thumbnailUrl);
            
            Map<String, String> response = new HashMap<>();
            // Return relative paths - frontend will resolve based on baseUrl
            response.put("videoUrl", videoUrl);
            response.put("thumbnailUrl", thumbnailUrl);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing video upload", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to process video: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Service method for getting a thumbnail URL for a video.
     * This centralizes thumbnail handling to avoid duplication across controllers.
     * 
     * @param videoUrl The URL of the video
     * @return The URL of the thumbnail
     */
    public String getThumbnailForVideo(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            logger.debug("No video path provided, returning default thumbnail");
            return storageService.getUrl("/thumbnails/default_thumbnail.jpg");
        }
        
        logger.debug("Getting thumbnail for video path: {}", videoPath);
        
        try {
            // Extract filename from path
            String videoFilename = videoPath;
            if (videoPath.contains("/")) {
                videoFilename = videoPath.substring(videoPath.lastIndexOf("/") + 1);
            }
            
            // Remove file extension to get base name
            String baseName = videoFilename;
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf("."));
            }
            
            // Generate thumbnail filename
            String thumbnailFilename = baseName + "_thumbnail.jpg";
            
            // Check if thumbnail already exists in storage
            String thumbnailPath = "/thumbnails/" + thumbnailFilename;
            if (storageService.exists(thumbnailPath)) {
                logger.debug("Thumbnail already exists: {}", thumbnailPath);
                return storageService.getUrl(thumbnailPath);
            }
            
            // Use ThumbnailService to generate thumbnail
            String generatedThumbnail = thumbnailService.generateThumbnail(videoPath, thumbnailFilename);
            
            if (generatedThumbnail != null) {
                logger.debug("Thumbnail generated successfully: {}", generatedThumbnail);
                return storageService.getUrl("/thumbnails/" + thumbnailFilename);
            } else {
                logger.error("Failed to generate thumbnail for video: {}", videoPath);
                return storageService.getUrl("/thumbnails/default_thumbnail.jpg");
            }
            
        } catch (Exception e) {
            logger.error("Error generating thumbnail for video: {}", e.getMessage(), e);
            return storageService.getUrl("/thumbnails/default_thumbnail.jpg");
        }
    }
    
    /**
     * REST endpoint for getting a thumbnail URL for a video.
     * This allows other parts of the application to retrieve thumbnails 
     * without duplicating the logic.
     * 
     * @param videoUrl The URL of the video to get the thumbnail for
     * @return A response containing the thumbnail URL
     */
    @GetMapping("/thumbnail")
    public ResponseEntity<Map<String, String>> getThumbnail(
            @RequestParam("videoUrl") String videoUrl) {
        logger.info("Thumbnail request received for video URL: {}", videoUrl);
        
        String thumbnailUrl = getThumbnailForVideo(videoUrl);
        
        Map<String, String> response = new HashMap<>();
        response.put("thumbnailUrl", thumbnailUrl);
        
        return ResponseEntity.ok(response);
    }
}
