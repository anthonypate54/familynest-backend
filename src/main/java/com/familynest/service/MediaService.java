package com.familynest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized service for all media handling operations including
 * uploads, thumbnail generation, and URL management.
 */
@Service
public class MediaService {
    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);
    
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
    
    @Value("${app.videos.dir:${file.upload-dir}/videos}")
    private String videosDir;
    
    @Value("${app.thumbnail.dir:${file.upload-dir}/thumbnails}")
    private String thumbnailDir;
    
    @Value("${app.url.videos:/uploads/videos}")
    private String videosUrlPath;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;
    
    @Value("${app.url.images:/uploads/images}")
    private String imagesUrlPath;
    
    @Value("${app.default.thumbnail:default_thumbnail.jpg}")
    private String defaultThumbnailFilename;
    
    @Autowired
    private ThumbnailService thumbnailService;
    
    /**
     * Get the default thumbnail URL
     */
    private String getDefaultThumbnailUrl() {
        return thumbnailsUrlPath + "/" + defaultThumbnailFilename;
    }
    
    /**
     * Uploads general media file (image or video) for messages
     * 
     * @param file The media file to upload
     * @param mediaType The type of media ("image" or "video")
     * @return Map containing mediaUrl and thumbnailUrl (for videos)
     * @throws IOException If file operations fail
     */
    public Map<String, String> uploadMedia(MultipartFile file, String mediaType) throws IOException {
        logger.debug("Processing media of type: {}", mediaType);
        
        // Create directory structure if it doesn't exist
        String subdir = "video".equals(mediaType) ? "videos" : "images";
        Path uploadPath = Paths.get(uploadDir, subdir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Create filename with timestamp
        String mediaFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path mediaPath = uploadPath.resolve(mediaFileName);
        
        // Write the file
        Files.write(mediaPath, file.getBytes());
        logger.debug("Media file saved at: {}", mediaPath);
        
        // Set media URL (relative path)
        String mediaUrl = "video".equals(mediaType) ? 
            videosUrlPath + "/" + mediaFileName : 
            "/uploads/images/" + mediaFileName;
        
        Map<String, String> result = new HashMap<>();
        result.put("mediaUrl", mediaUrl);
        
        // For videos, generate thumbnail
        if ("video".equals(mediaType)) {
            // Create thumbnails directory if needed
            Path thumbnailDirPath = Paths.get(thumbnailDir);
            if (!Files.exists(thumbnailDirPath)) {
                Files.createDirectories(thumbnailDirPath);
            }
            
            // Generate thumbnail filename - handle any video extension
            String baseName = mediaFileName.substring(0, mediaFileName.lastIndexOf('.'));
            String thumbnailFileName = baseName + "_thumbnail.jpg";
            Path thumbnailPath = thumbnailDirPath.resolve(thumbnailFileName);
            
            // Generate thumbnail using ThumbnailService
            boolean thumbnailCreated = false;
            try {
                logger.debug("Generating thumbnail for video: {}", mediaPath);
                String generatedThumbnailPath = thumbnailService.generateThumbnail(
                    mediaPath.toString(), thumbnailFileName);
                thumbnailCreated = generatedThumbnailPath != null;
                
                // Add a verification step to ensure thumbnail file exists
                if (thumbnailCreated) {
                    // Extract the filename from the generated path
                    String extractedFilename = generatedThumbnailPath.substring(generatedThumbnailPath.lastIndexOf('/') + 1);
                    Path verifyThumbnailPath = Paths.get(thumbnailDir, extractedFilename);
                    
                    // Verify the thumbnail file actually exists on disk
                    int maxRetries = 5;
                    int retryCount = 0;
                    while (!Files.exists(verifyThumbnailPath) && retryCount < maxRetries) {
                        logger.debug("Waiting for thumbnail file to appear on disk: {}", verifyThumbnailPath);
                        try {
                            Thread.sleep(100); // Wait 100ms
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.warn("Thread interrupted while waiting for thumbnail", ie);
                            break;
                        }
                        retryCount++;
                    }
                    
                    if (Files.exists(verifyThumbnailPath)) {
                        logger.debug("Verified thumbnail exists on disk: {}", verifyThumbnailPath);
                    } else {
                        logger.warn("Could not verify thumbnail exists after {} retries: {}", maxRetries, verifyThumbnailPath);
                        thumbnailCreated = false;
                    }
                }
            } catch (Exception ex) {
                logger.error("Error generating thumbnail: {}", ex.getMessage(), ex);
            }
            
            if (thumbnailCreated) {
                // Set thumbnail URL (relative path)
                String thumbnailUrl = thumbnailsUrlPath + "/" + thumbnailFileName;
                result.put("thumbnailUrl", thumbnailUrl);
                logger.debug("Thumbnail created at: {}", thumbnailPath);
            } else {
                logger.warn("Failed to create thumbnail for video");
                result.put("thumbnailUrl", getDefaultThumbnailUrl());
            }
        }
        
        logger.debug("Media upload complete. Result: {}", result);
        return result;
    }

    /**
     * Handles external video URLs with uploaded thumbnails
     * This is a separate method to keep it clean and avoid confusing the uploadMedia method
     * 
     * @param thumbnailFile The thumbnail image file uploaded from frontend
     * @param externalVideoUrl The external video URL (Google Drive, etc.)
     * @return Map containing mediaUrl (external URL), thumbnailUrl, and mediaType
     * @throws IOException If thumbnail upload fails
     */
    public Map<String, String> processExternalVideoWithThumbnail(
            MultipartFile thumbnailFile, 
            String externalVideoUrl) throws IOException {
        
        logger.debug("Processing external video URL: {} with uploaded thumbnail", externalVideoUrl);
        
        // Create thumbnails directory if needed
        Path thumbnailDirPath = Paths.get(thumbnailDir);
        if (!Files.exists(thumbnailDirPath)) {
            Files.createDirectories(thumbnailDirPath);
        }
        
        // Create filename with timestamp for the thumbnail
        String thumbnailFileName = System.currentTimeMillis() + "_" + thumbnailFile.getOriginalFilename();
        Path thumbnailPath = thumbnailDirPath.resolve(thumbnailFileName);
        
        // Write the thumbnail file to disk
        Files.write(thumbnailPath, thumbnailFile.getBytes());
        logger.debug("Thumbnail file saved at: {}", thumbnailPath);
        
        // Create result map
        Map<String, String> result = new HashMap<>();
        result.put("mediaUrl", externalVideoUrl);  // External URL is the main media URL
        result.put("thumbnailUrl", thumbnailsUrlPath + "/" + thumbnailFileName);  // Local thumbnail URL
        result.put("mediaType", "cloud_video");  // Special type for external videos
        
        logger.debug("External video processing complete. Result: {}", result);
        return result;
    }
} 