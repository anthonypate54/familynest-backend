package com.familynest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.familynest.service.storage.StorageService;
import com.familynest.service.storage.S3StorageService;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
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
            String tempVideoPath = null;
            try {
                logger.debug("Generating thumbnail for video: {}", storedPath);
                
                // Handle S3 vs Local storage differently for thumbnail generation
                String videoPathForThumbnail;
                if (storageService instanceof S3StorageService) {
                    // For S3, download the video temporarily
                    S3StorageService s3Service = (S3StorageService) storageService;
                    tempVideoPath = s3Service.downloadTemporarily(storedPath);
                    if (tempVideoPath == null) {
                        logger.error("Failed to download video from S3 for thumbnail generation: {}", storedPath);
                        result.put("thumbnailUrl", getDefaultThumbnailUrl());
                        return result;
                    }
                    videoPathForThumbnail = tempVideoPath;
                    logger.debug("Downloaded video from S3 to temporary location: {}", tempVideoPath);
                } else {
                    // For local storage, use the absolute path directly
                    videoPathForThumbnail = storageService.getAbsolutePath(storedPath).toString();
                }
                
                logger.error("🎯 DEBUG: Video path for thumbnail generation: {}", videoPathForThumbnail);
                String generatedThumbnailPath = thumbnailService.generateThumbnail(
                    videoPathForThumbnail, thumbnailFileName);
                thumbnailCreated = generatedThumbnailPath != null;
                
                // Upload the generated thumbnail to S3 and get URL
                if (thumbnailCreated) {
                    try {
                        // The ThumbnailService generated the file locally, now upload it to S3
                        String localThumbnailPath = System.getProperty("user.dir") + "/uploads/thumbnails/" + thumbnailFileName;
                        File thumbnailFile = new File(localThumbnailPath);
                        
                        if (thumbnailFile.exists()) {
                            logger.debug("Uploading thumbnail to S3: {}", localThumbnailPath);
                            
                            // For S3 storage, directly upload the thumbnail file  
                            if (storageService instanceof S3StorageService) {
                                S3StorageService s3Service = (S3StorageService) storageService;
                                
                                // Upload file directly to S3 using putObject
                                try (FileInputStream fis = new FileInputStream(thumbnailFile)) {
                                    String key = "thumbnails/" + thumbnailFileName;
                                    com.amazonaws.services.s3.model.ObjectMetadata metadata = new com.amazonaws.services.s3.model.ObjectMetadata();
                                    metadata.setContentLength(thumbnailFile.length());
                                    metadata.setContentType("image/jpeg");
                                    
                                    s3Service.getS3Client().putObject(s3Service.getBucketName(), key, fis, metadata);
                                    logger.info("Stored thumbnail {} in S3 bucket: {}", thumbnailFileName, s3Service.getBucketName());
                                    
                                    String thumbnailUrl = s3Service.getUrl("/" + key);
                                    result.put("thumbnailUrl", thumbnailUrl);
                                    logger.debug("Thumbnail uploaded to S3: {}", thumbnailUrl);
                                } catch (Exception e) {
                                    logger.error("Failed to upload thumbnail directly to S3: {}", e.getMessage(), e);
                                    result.put("thumbnailUrl", getDefaultThumbnailUrl());
                                }
                        } else {
                            // For local storage, the thumbnail is already in the right place
                            // Just use the generated thumbnail URL directly
                            logger.info("Using locally generated thumbnail: {}", generatedThumbnailPath);
                            result.put("thumbnailUrl", generatedThumbnailPath);
                        }
                            
                            // Clean up local file (only for S3, not for local storage)
                            if (storageService instanceof S3StorageService) {
                                thumbnailFile.delete();
                            }
                        } else {
                            logger.error("Local thumbnail file not found: {}", localThumbnailPath);
                            result.put("thumbnailUrl", getDefaultThumbnailUrl());
                        }
                    } catch (Exception uploadEx) {
                        logger.error("Failed to upload thumbnail to S3: {}", uploadEx.getMessage(), uploadEx);
                        result.put("thumbnailUrl", getDefaultThumbnailUrl());
                    }
                }
            } catch (Exception ex) {
                logger.error("Error generating thumbnail: {}", ex.getMessage(), ex);
            } finally {
                // Clean up temporary video file if it was downloaded from S3
                if (tempVideoPath != null && storageService instanceof S3StorageService) {
                    S3StorageService s3Service = (S3StorageService) storageService;
                    s3Service.cleanupTempFile(tempVideoPath);
                }
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