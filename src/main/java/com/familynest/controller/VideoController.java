package com.familynest.controller;

import com.familynest.service.ThumbnailService;
// Temporarily commented out to fix authentication issues
// import com.familynest.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;
    
    @Value("${app.videos.dir:${file.upload-dir}/videos}")
    private String videosDir;
    
    @Value("${app.thumbnail.dir:${file.upload-dir}/thumbnails}")
    private String thumbnailDir;
    
    @Value("${app.url.videos:/uploads/videos}")
    private String videosUrlPath;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;
    
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
     * Simple test endpoint for uploader to test connectivity
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        logger.info("VIDEO TEST ENDPOINT ACCESSED");
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("videoUrl", videosUrlPath + "/test_video.mp4");
        response.put("thumbnailUrl", thumbnailsUrlPath + "/default_thumbnail.jpg");
        return ResponseEntity.ok(response);
    }

    /**
     * Upload endpoint - saves the video and generates a thumbnail
     */
    @PostMapping("/upload")
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

            // Store video file to the videos subdirectory
            Path videosDirPath = Paths.get(videosDir);
            if (!Files.exists(videosDirPath)) {
                Files.createDirectories(videosDirPath);
            }
            
            Path videoPath = videosDirPath.resolve(filename);
            Files.copy(file.getInputStream(), videoPath);
            logger.debug("Video saved at path: {}", videoPath);
            
            // Create proper relative URLs for frontend
            String videoUrl = videosUrlPath + "/" + filename;
            
            // Generate a thumbnail for the video
            String thumbnailUrl = getThumbnailForVideo(videoPath.toString());
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
     * Test endpoint to debug thumbnail issue
     */
    @GetMapping("/debug/{id}")
    public ResponseEntity<Map<String, Object>> debugMessage(@PathVariable Long id) {
        logger.error("DEBUG ENDPOINT ACCESSED for message ID: {}", id);
        
        try {
            String sql = "SELECT id, content, media_type, media_url, thumbnail_url FROM message WHERE id = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, id);
            
            logger.error("DEBUG: Database query returned: {}", result);
            logger.error("DEBUG: Keys in result: {}", result.keySet());
            logger.error("DEBUG: thumbnail_url in result? {}", result.containsKey("thumbnail_url"));
            logger.error("DEBUG: thumbnail_url value: {}", result.get("thumbnail_url"));
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in debug endpoint: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Test JSON endpoint to debug conversion issues
     */
    @GetMapping("/test-json")
    public ResponseEntity<Map<String, Object>> testJsonEndpoint() {
        logger.error("TEST JSON ENDPOINT ACCESSED");
        
        // Create a test message
        Map<String, Object> message = new HashMap<>();
        message.put("id", 12345L);
        message.put("content", "Test video message");
        message.put("media_type", "video");
        message.put("mediaType", "video");  // duplicate with camelCase
        message.put("media_url", videosUrlPath + "/test.mp4");
        message.put("mediaUrl", videosUrlPath + "/test.mp4");  // duplicate with camelCase
        message.put("thumbnail_url", thumbnailsUrlPath + "/test_thumb.jpg");
        message.put("thumbnailUrl", thumbnailsUrlPath + "/test_thumb.jpg");  // duplicate with camelCase
        
        // Add it to a response
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("testCamelCase", "This is camelCase");
        response.put("test_snake_case", "This is snake_case");
        
        // Log what we're returning
        logger.error("TEST JSON RESPONSE: {}", response);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Public test endpoint that shouldn't require authentication
     */
    @GetMapping("/public-test")
    @CrossOrigin(origins = "*")
    public ResponseEntity<Map<String, Object>> publicTestEndpoint() {
        logger.info("PUBLIC VIDEO TEST ENDPOINT ACCESSED - This should not require auth");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "This endpoint is public and should not require authentication");
        
        // Add video info with thumbnail
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("videoUrl", videosUrlPath + "/test_video.mp4");
        videoInfo.put("thumbnailUrl", thumbnailsUrlPath + "/default_thumbnail.jpg");
        response.put("video", videoInfo);
        
        logger.info("Returning public test response: {}", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Service method for getting a thumbnail URL for a video.
     * This centralizes thumbnail handling to avoid duplication across controllers.
     * 
     * @param videoUrl The URL of the video
     * @return The URL of the thumbnail
     */
    public String getThumbnailForVideo(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            logger.debug("No video URL provided, returning default thumbnail");
            return thumbnailsUrlPath + "/default_thumbnail.jpg";
        }
        
        logger.debug("Getting thumbnail for video URL: {}", videoUrl);
        
        try {
            // Extract filename from URL and handle path prefix variations
            String videoFilename;
            String physicalVideoPath;
            
            // Handle full URLs, relative paths, and prefixed paths
            if (videoUrl.contains("/")) {
                // Extract just the filename
                videoFilename = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
                
                // For paths that have our known prefix, we need to map to the physical path
                if (videoUrl.startsWith(videosUrlPath)) {
                    // Convert from URL path to physical path
                    physicalVideoPath = Paths.get(videosDir, videoFilename).toString();
                } else {
                    // Assume it's already a physical path
                    physicalVideoPath = videoUrl;
                }
            } else {
                videoFilename = videoUrl;
                physicalVideoPath = Paths.get(videosDir, videoFilename).toString();
            }
            
            // Remove any query parameters
            if (videoFilename.contains("?")) {
                videoFilename = videoFilename.substring(0, videoFilename.indexOf("?"));
            }
            
            // Check if thumbnail already exists
            String baseName = videoFilename;
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf("."));
            }
            
            // Check for timestamp_uniqueid pattern to extract
            Pattern pattern = Pattern.compile("(\\d+_[a-zA-Z0-9]+)");
            Matcher matcher = pattern.matcher(baseName);
            String filePrefix = matcher.find() ? matcher.group(1) : baseName;
            
            String thumbnailFilename = filePrefix + "_thumb.jpg";
            String thumbnailRelativePath = thumbnailsUrlPath + "/" + thumbnailFilename;
            
            // Check if thumbnail exists
            Path thumbnailFullPath = Paths.get(thumbnailDir, thumbnailFilename);
            if (Files.exists(thumbnailFullPath)) {
                logger.debug("Thumbnail already exists at: {}", thumbnailFullPath);
                return thumbnailRelativePath;
            }
            
            // Get actual video file path, avoiding duplications
            Path videoFullPath;
            
            // Create a path check string by removing the leading slash from the URL path
            String videoUrlPathCheck = videosUrlPath.startsWith("/") ? 
                videosUrlPath.substring(1) : videosUrlPath;
                
            // If the URL is a reference to a file in our uploads directory
            if (videoUrl.contains(videoUrlPathCheck)) {
                // Only use the filename to avoid path duplication
                videoFullPath = Paths.get(videosDir, videoFilename);
                logger.debug("Extracted video path from URL: {}", videoFullPath);
            } else if (videoUrl.startsWith("/")) {
                // Handle absolute path within the server
                if (videoUrl.startsWith(uploadDir)) {
                    // Already an absolute path to the file
                    videoFullPath = Paths.get(videoUrl);
                } else {
                    // Add upload dir prefix
                    videoFullPath = Paths.get(uploadDir, videoUrl.substring(1));
                }
                logger.debug("Created video path from absolute path: {}", videoFullPath);
            } else {
                // Handle relative path
                videoFullPath = Paths.get(videosDir, videoFilename);
                logger.debug("Created video path from relative path: {}", videoFullPath);
            }
            
            // Ensure the video file exists
            if (!Files.exists(videoFullPath)) {
                logger.error("Video file does not exist at path: {}", videoFullPath);
                return thumbnailsUrlPath + "/default_thumbnail.jpg";
            }
            
            logger.debug("Using ThumbnailService to generate thumbnail for video at: {}", videoFullPath);
            
            // Generate thumbnail - pass the video file path and just the filename (not full path)
            // This avoids the ThumbnailService from duplicating paths
            String generatedThumbnail = thumbnailService.generateThumbnail(
                videoFullPath.toString(), thumbnailFilename);
            
            if (generatedThumbnail != null) {
                logger.debug("Thumbnail generated successfully: {}", generatedThumbnail);
                return generatedThumbnail;
            } else {
                logger.error("Failed to generate thumbnail for video: {}", videoUrl);
                return thumbnailsUrlPath + "/default_thumbnail.jpg";
            }
            
        } catch (Exception e) {
            logger.error("Error generating thumbnail for video: {}", e.getMessage(), e);
            return thumbnailsUrlPath + "/default_thumbnail.jpg";
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

 