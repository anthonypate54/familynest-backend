package com.familynest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.familynest.controller.VideoController;

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
    private VideoController videoController;
    
    /**
     * Get the default thumbnail URL
     */
    private String getDefaultThumbnailUrl() {
        return thumbnailsUrlPath + "/" + defaultThumbnailFilename;
    }
    
    /**
     * Uploads a video file and generates a thumbnail
     * 
     * @param file The video file to upload
     * @return Map containing videoUrl and thumbnailUrl
     * @throws IOException If file operations fail
     */
    public Map<String, String> uploadVideo(MultipartFile file) throws IOException {
        // Create a unique filename
        String originalFilename = file.getOriginalFilename();
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        String filename = timestamp + "_" + uniqueId + 
                        (originalFilename != null ? "_" + originalFilename : "_video.mp4");
        
        // Create directories if they don't exist
        Path uploadPath = Paths.get(videosDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Save the video file
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, file.getBytes());
        logger.info("Video saved successfully: {}", filePath);
        
        // Generate thumbnail using the service
        String thumbnailFilename = timestamp + "_" + uniqueId + "_thumb.jpg";
        logger.info("Generating thumbnail with filename: {}", thumbnailFilename);
        
        String videoUrl = videosUrlPath + "/" + filename;
        String thumbnailUrl = videoController.getThumbnailForVideo(videoUrl);
        
        logger.info("VideoController returned URL: {}", thumbnailUrl);
        
        // Use the returned URL or default
        if (thumbnailUrl == null) {
            logger.warn("Failed to generate thumbnail, using default");
            thumbnailUrl = getDefaultThumbnailUrl();
        }
        
        // Return URLs
        Map<String, String> result = new HashMap<>();
        result.put("videoUrl", videoUrl);
        result.put("thumbnailUrl", thumbnailUrl);
        result.put("videoPath", filePath.toString());
        
        return result;
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
        // Create a unique filename
        String originalFilename = file.getOriginalFilename();
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        logger.debug("Uploading media: originalFilename={}, mediaType={}, size={} bytes", 
            originalFilename, mediaType, file.getSize());
        
        String filename = timestamp + "_" + uniqueId + 
                        (originalFilename != null ? "_" + originalFilename : "_media");
        
        // Create directories if they don't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created uploads directory: {}", uploadPath);
        }
        
        // Save the media file
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, file.getBytes());
        logger.info("Media file saved successfully: {}", filePath);
        
        Map<String, String> result = new HashMap<>();
        
        // Always use relative paths for URLs - makes transition to S3 easier
        if ("video".equals(mediaType)) {
            logger.error("MEDIA SERVICE: Processing video file: {}, size: {} bytes", filename, file.getSize());
            
            // For video files, create a copy in the videos directory too
            Path videosPath = Paths.get(videosDir);
            if (!Files.exists(videosPath)) {
                Files.createDirectories(videosPath);
                logger.info("Created videos directory: {}", videosPath);
            }
            
            // Save a copy to the videos directory
            Path videoPath = videosPath.resolve(filename);
            Files.copy(filePath, videoPath);
            logger.info("Video file copied to videos directory: {}", videoPath);
            
            // Set relative URL for videos
            String videoUrl = videosUrlPath + "/" + filename;
            result.put("mediaUrl", videoUrl);
            
            try {
                // Use VideoController to get the thumbnail URL (centralized handling)
                String thumbnailUrl = videoController.getThumbnailForVideo(videoUrl);
                logger.error("MEDIA SERVICE: Got thumbnail URL from VideoController: {}", thumbnailUrl);
                result.put("thumbnailUrl", thumbnailUrl);
            } catch (Exception e) {
                logger.error("Error getting thumbnail from VideoController: {}", e.getMessage(), e);
                result.put("thumbnailUrl", getDefaultThumbnailUrl());
            }
        } else {
            // For non-video files (images, etc.)
            result.put("mediaUrl", imagesUrlPath + "/" + filename);
        }
        
        logger.debug("Media upload complete. Result: {}", result);
        return result;
    }
} 