package com.familynest.controller;

import com.familynest.service.ThumbnailService;
import com.familynest.service.storage.StorageService;
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
    
    @Autowired
    private ThumbnailService thumbnailService;
    
    @Autowired
    private StorageService storageService;
    
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
        response.put("videoUrl", "/uploads/test_video.mp4");
        response.put("thumbnailUrl", "/uploads/thumbnails/default_thumbnail.jpg");
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

            // Store video file using storage service
            String videoPath = uploadDir + "/" + filename;
            Files.copy(file.getInputStream(), Paths.get(videoPath));
            logger.debug("Video saved at path: {}", videoPath);
            
            // Generate a thumbnail for the video using the relative path
            String thumbnailUrl = getThumbnailForVideo(videoPath);
            logger.debug("Generated thumbnail URL: {}", thumbnailUrl);
            
            Map<String, String> response = new HashMap<>();
            // Return relative paths - frontend will resolve based on baseUrl
            response.put("videoUrl", videoPath);
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
        message.put("media_url", "/uploads/videos/test.mp4");
        message.put("mediaUrl", "/uploads/videos/test.mp4");  // duplicate with camelCase
        message.put("thumbnail_url", "/uploads/thumbnails/test_thumb.jpg");
        message.put("thumbnailUrl", "/uploads/thumbnails/test_thumb.jpg");  // duplicate with camelCase
        
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
        videoInfo.put("videoUrl", "/uploads/test_video.mp4");
        videoInfo.put("thumbnailUrl", "/uploads/thumbnails/default_thumbnail.jpg");
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
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
        
        logger.debug("Getting thumbnail for video URL: {}", videoUrl);
        
        try {
            // Extract filename from URL
            String videoFilename;
            
            // Handle both full URLs and relative paths
            if (videoUrl.contains("/")) {
                videoFilename = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
            } else {
                videoFilename = videoUrl;
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
            String thumbnailRelativePath = "/uploads/thumbnails/" + thumbnailFilename;
            
            // Check if thumbnail exists
            if (Files.exists(Paths.get(uploadDir + "/" + thumbnailFilename))) {
                logger.debug("Thumbnail already exists at: {}", thumbnailRelativePath);
                return thumbnailRelativePath;
            }
            
            // If video path is absolute, convert to relative
            String relativeVideoPath = videoUrl;
            if (videoUrl.startsWith("http") || videoUrl.startsWith("/uploads/")) {
                // Extract just the filename
                relativeVideoPath = "videos/" + videoFilename;
            }
            
            // Generate thumbnail - pass actual paths within the file system
            Path videoFullPath = Paths.get(uploadDir + "/" + relativeVideoPath);
            
            if (Files.exists(videoFullPath)) {
                // Use FFmpeg to generate thumbnail
                String newThumbnailPath = thumbnailService.generateThumbnail(
                    videoFullPath.toString(),
                    thumbnailFilename
                );
                
                if (newThumbnailPath != null) {
                    logger.debug("Generated new thumbnail at: {}", newThumbnailPath);
                    return "/uploads/thumbnails/" + thumbnailFilename;
                }
            } else {
                logger.error("Video file not found at path: {}", videoFullPath);
            }
            
            // Fallback to default thumbnail if generation fails
            logger.debug("Using default thumbnail");
            return "/uploads/thumbnails/default_thumbnail.jpg";
            
        } catch (Exception e) {
            logger.error("Error generating thumbnail for video: {}", e.getMessage(), e);
            return "/uploads/thumbnails/default_thumbnail.jpg";
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

    /**
     * Direct endpoint to serve thumbnail files
     * This provides a workaround for static resource issues
     */
    @GetMapping("/public/thumbnail/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        logger.debug("Serving thumbnail file: {}", filename);
        
        try {
            // Load file as resource using storage service
            Resource file = storageService.loadAsResource("thumbnails/" + filename);
            
            if (file == null || !file.exists()) {
                logger.error("Thumbnail file not found: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            // Try to determine content type
            String contentType = "image/jpeg";  // Default for thumbnails
            
            logger.debug("Serving thumbnail file: {} with content type: {}", filename, contentType);
            
            // Return the file
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(file);
        } catch (Exception e) {
            logger.error("Error serving thumbnail file: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 