package com.familynest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.familynest.service.storage.StorageService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized service for all media handling operations including
 * uploads, thumbnail generation, and URL management.
 */
@Service
public class MediaService {
    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);
    
    @Autowired
    private StorageService storageService;
    
    @Autowired
    private ThumbnailService thumbnailService;
    
    /**
     * Get the default thumbnail URL
     */
    private String getDefaultThumbnailUrl() {
        return storageService.getUrl("/thumbnails/default_thumbnail.jpg");
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
        logger.error("🚀 MEDIA SERVICE: uploadMedia called with mediaType: {}", mediaType);
        logger.debug("Processing media of type: {}", mediaType);
        
        // Determine directory based on media type
        String directory = "video".equals(mediaType) ? "videos" : "images";
        
        // Create filename with timestamp
        String mediaFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        
        // Store the file using StorageService
        String storedPath = storageService.store(file, directory, mediaFileName);
        logger.debug("Media file saved at: {}", storedPath);
        
        // Get the URL from StorageService
        String mediaUrl = storageService.getUrl(storedPath);
        
        Map<String, String> result = new HashMap<>();
        result.put("mediaUrl", mediaUrl);
        
        // For videos, generate thumbnail
        if ("video".equals(mediaType)) {
            
            // Generate thumbnail filename - handle any video extension
            String baseName = mediaFileName.substring(0, mediaFileName.lastIndexOf('.'));
            String thumbnailFileName = baseName + "_thumbnail.jpg";
            
            // Generate thumbnail using ThumbnailService
            boolean thumbnailCreated = false;
            try {
                logger.debug("Generating thumbnail for video: {}", storedPath);
                // Convert relative path to absolute path for FFmpeg
                String absoluteVideoPath = storageService.getAbsolutePath(storedPath).toString();
                logger.error("🎯 DEBUG: Absolute video path for thumbnail: {}", absoluteVideoPath);
                String generatedThumbnailPath = thumbnailService.generateThumbnail(
                    absoluteVideoPath, thumbnailFileName);
                thumbnailCreated = generatedThumbnailPath != null;
                
                // Use StorageService to get thumbnail URL
                if (thumbnailCreated) {
                    result.put("thumbnailUrl", storageService.getUrl("/thumbnails/" + thumbnailFileName));
                }
            } catch (Exception ex) {
                logger.error("Error generating thumbnail: {}", ex.getMessage(), ex);
            }
            
            if (!thumbnailCreated) {
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
        
        // Create filename with timestamp for the thumbnail
        String thumbnailFileName = System.currentTimeMillis() + "_" + thumbnailFile.getOriginalFilename();
        String thumbnailPath = "/thumbnails/" + thumbnailFileName;
        
        // Store the thumbnail using StorageService
        storageService.store(thumbnailFile, "/thumbnails", thumbnailFileName);
        logger.debug("Thumbnail file saved at: {}", thumbnailPath);
        
        // Create result map
        Map<String, String> result = new HashMap<>();
        result.put("mediaUrl", externalVideoUrl);  // External URL is the main media URL
        result.put("thumbnailUrl", storageService.getUrl(thumbnailPath));  // Get thumbnail URL from storage service
        result.put("mediaType", "cloud_video");  // Special type for external videos
        
        logger.debug("External video processing complete. Result: {}", result);
        return result;
    }
} 