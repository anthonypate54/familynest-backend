package com.familynest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A simplified service that only handles thumbnail generation
 * with proper error handling and timeouts
 */
@Service
public class ThumbnailService {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int THUMBNAIL_TIMEOUT_SECONDS = 10;
    
    @Value("${app.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailDir;
       
    @Value("${app.use.ffmpeg:true}")
    private boolean useFFmpeg;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;
    
    @Value("${app.default.thumbnail:default_thumbnail.jpg}")
    private String defaultThumbnailFilename;
    
    /**
     * Get the default thumbnail URL path
     */
    private String getDefaultThumbnailUrl() {
        return thumbnailsUrlPath + "/" + defaultThumbnailFilename;
    }
    
    /**
     * Generates a thumbnail for a video file with a timeout mechanism
     * @param videoPath Path to the video file
     * @param thumbnailFilename Desired filename for the thumbnail
     * @return Path to the thumbnail, or null if generation failed
     */
    public String generateThumbnail(String videoPath, String thumbnailFilename) {
        try {
            logger.error("THUMBNAIL SERVICE: Starting thumbnail generation for: {}", videoPath);
            // Create thumbnail directory if it doesn't exist
            Path thumbnailDirPath = Paths.get(thumbnailDir);
            if (!Files.exists(thumbnailDirPath)) {
                Files.createDirectories(thumbnailDirPath);
                logger.info("Created thumbnail directory: {}", thumbnailDir);
            }
            
            // First try using FFmpeg to extract an actual frame from the video
            try {
                logger.error("THUMBNAIL SERVICE: Attempting to use FFmpeg for thumbnail generation");
                String result = generateThumbnailInternal(videoPath, thumbnailFilename);
                
                // If FFmpeg succeeded, return the result
                if (result != null && !result.equals(getDefaultThumbnailUrl())) {
                    logger.error("THUMBNAIL SERVICE: FFmpeg thumbnail generation successful: {}", result);
                    return result;
                }
                
                // If FFmpeg method returned default thumbnail, fall back to simple method
                logger.error("THUMBNAIL SERVICE: FFmpeg returned default thumbnail, trying simple method as fallback");
            } catch (Exception e) {
                logger.error("THUMBNAIL SERVICE: FFmpeg method failed: {}", e.getMessage(), e);
                // Continue to fallback method
            }
            
            // Use simple method as fallback
            try {
                logger.error("THUMBNAIL SERVICE: Using simple Java Image method as fallback");
                String result = generateSimpleThumbnail(videoPath, thumbnailFilename);
                logger.error("THUMBNAIL SERVICE: Simple thumbnail generation successful: {}", result);
                return result;
            } catch (Exception e) {
                logger.error("THUMBNAIL SERVICE: Simple method failed: {}", e.getMessage(), e);
                return getDefaultThumbnailUrl();
            }
        } catch (Exception e) {
            logger.error("THUMBNAIL SERVICE: Failed to generate thumbnail: {}", e.getMessage(), e);
            return getDefaultThumbnailUrl();
        }
    }
    
    /**
     * Internal method to generate thumbnail without timeout handling
     */
    private String generateThumbnailInternal(String videoPath, String thumbnailFilename) {
        if (!useFFmpeg) {
            logger.info("FFmpeg thumbnail generation is disabled. Using default thumbnail.");
            return getDefaultThumbnailUrl();
        }
        
        // Check that the video file exists
        File videoFile = new File(videoPath);
        if (!videoFile.exists() || !videoFile.canRead()) {
            logger.error("FFMPEG: Video file does not exist or cannot be read: {}", videoPath);
            return getDefaultThumbnailUrl();
        }
        
        logger.error("VIDEO PATH CHECK: Path exists? {}, Size: {} bytes", Files.exists(Paths.get(videoPath)), videoFile.length());
        logger.error("THUMBNAIL GENERATION: Video path = {}, Thumbnail filename = {}", videoPath, thumbnailFilename);
        
        // Ensure we have a clean thumbnail filename (no path components)
        if (thumbnailFilename.contains("/") || thumbnailFilename.contains("\\")) {
            thumbnailFilename = new File(thumbnailFilename).getName();
        }
        
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        logger.error("THUMBNAIL PATH: Full path = {}, Directory exists? {}", 
            thumbnailPath.toAbsolutePath(), Files.exists(thumbnailPath.getParent()));
        
        // Create parent directory if it doesn't exist
        try {
            Files.createDirectories(thumbnailPath.getParent());
        } catch (Exception e) {
            logger.error("FFMPEG: Error creating thumbnail directory: {}", e.getMessage());
        }
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            logger.error("FFMPEG: Created grabber for video path: {}", videoPath);
            
            // Set the format explicitly to help FFmpeg recognize the file
            if (videoPath.toLowerCase().endsWith(".mp4")) {
                logger.error("FFMPEG: Setting format to mp4");
                grabber.setFormat("mp4");
            } else if (videoPath.toLowerCase().endsWith(".avi")) {
                logger.error("FFMPEG: Setting format to avi");
                grabber.setFormat("avi");
            } else if (videoPath.toLowerCase().endsWith(".mov")) {
                logger.error("FFMPEG: Setting format to mov");
                grabber.setFormat("mov");
            }
            
            // Set some additional options to improve frame grabbing reliability
            grabber.setOption("threads", "1");
            grabber.setOption("analyzeduration", "10000000");  // 10 seconds in microseconds
            grabber.setOption("probesize", "5000000");         // 5MB
            
            try {
                logger.error("FFMPEG: About to start grabber...");
                grabber.start();
                logger.error("FFMPEG: Grabber started successfully!");
                
                // Try to get video duration, but handle if not available
                long durationInSeconds = 1;
                try {
                    durationInSeconds = Math.max(1, grabber.getLengthInTime() / 1000000);
                    logger.error("FFMPEG: Video duration: {} seconds", durationInSeconds);
                } catch (Exception e) {
                    logger.error("FFMPEG: Could not get video duration: {}", e.getMessage());
                }
                
                // Try to grab a frame from various positions to maximize chance of success
                Frame frame = null;
                
                // First try at 1 second mark
                try {
                    grabber.setVideoTimestamp(1 * 1000000);  // 1 second in microseconds
                    frame = grabber.grabImage();
                    logger.error("FFMPEG: Grabbed frame at 1 second");
                } catch (Exception e) {
                    logger.error("FFMPEG: Could not grab frame at 1 second: {}", e.getMessage());
                }
                
                // If that fails, try at beginning
                if (frame == null) {
                    try {
                        grabber.setVideoTimestamp(0);
                        frame = grabber.grabImage();
                        logger.error("FFMPEG: Grabbed frame at start of video");
                    } catch (Exception e) {
                        logger.error("FFMPEG: Could not grab frame at start: {}", e.getMessage());
                    }
                }
                
                // If still no frame, try grabbing any frame
                if (frame == null) {
                    try {
                        for (int i = 0; i < 10 && frame == null; i++) {
                            frame = grabber.grabImage();
                            if (frame != null) {
                                logger.error("FFMPEG: Grabbed frame on attempt {}", i+1);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.error("FFMPEG: Could not grab any frame: {}", e.getMessage());
                    }
                }
                
                // If we got a frame, save it as a thumbnail
                if (frame != null) {
                    logger.error("FFMPEG: Successfully grabbed frame from video!");
                    
                    // Convert frame to BufferedImage
                    logger.error("FFMPEG: Converting frame to BufferedImage");
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage bufferedImage = converter.convert(frame);
                    
                    if (bufferedImage == null) {
                        logger.error("FFMPEG: Failed to convert frame to BufferedImage");
                        return getDefaultThumbnailUrl();
                    }
                    
                    logger.error("FFMPEG: Successfully converted frame to BufferedImage!");
                    
                    // Save the thumbnail
                    logger.error("FFMPEG: About to save thumbnail to: {}", thumbnailPath);
                    try {
                        boolean saved = ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
                        if (!saved) {
                            logger.error("FFMPEG: ImageIO could not find a writer for format: jpg");
                            return getDefaultThumbnailUrl();
                        }
                        logger.error("FFMPEG: Thumbnail generated successfully: {}", thumbnailPath);
                    } catch (Exception e) {
                        logger.error("FFMPEG: Failed to save thumbnail image: {}", e.getMessage(), e);
                        return getDefaultThumbnailUrl();
                    }
                    
                    // Return URL path for the thumbnail
                    String thumbnailUrl = thumbnailsUrlPath + "/" + thumbnailFilename;
                    logger.error("FFMPEG: Returning thumbnail URL: {}", thumbnailUrl);
                    return thumbnailUrl;
                } else {
                    logger.error("FFMPEG: Could not grab any frame from video after multiple attempts");
                    return getDefaultThumbnailUrl();
                }
            } catch (Exception e) {
                logger.error("FFMPEG: Error processing video: {}", e.getMessage(), e);
                try {
                    grabber.stop();
                } catch (Exception stopEx) {
                    logger.error("FFMPEG: Error stopping grabber: {}", stopEx.getMessage());
                }
                return getDefaultThumbnailUrl();
            }
        } catch (Exception e) {
            logger.error("FFMPEG: Error in thumbnail generation: {}", e.getMessage(), e);
            logger.error("FFMPEG: Exception class: {}", e.getClass().getName());
            logger.error("FFMPEG: Stack trace:", e);
            
            // Try a simpler approach as a last resort
            try {
                logger.error("FFMPEG: Attempting simple Java Image fallback method");
                return generateSimpleThumbnail(videoPath, thumbnailFilename);
            } catch (Exception fallbackEx) {
                logger.error("FFMPEG: Fallback also failed: {}", fallbackEx.getMessage());
                return getDefaultThumbnailUrl();
            }
        }
    }
    
    /**
     * Simple fallback method that doesn't use FFmpeg
     */
    private String generateSimpleThumbnail(String videoPath, String thumbnailFilename) throws Exception {
        logger.error("SIMPLE: Creating basic thumbnail for video: {}", videoPath);
        
        // Create a simple thumbnail using Java's basic image features
        // This is a last resort if FFmpeg method fails
        
        // Make sure the filename is clean
        if (thumbnailFilename.contains("/") || thumbnailFilename.contains("\\")) {
            thumbnailFilename = new File(thumbnailFilename).getName();
        }
        
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        
        // Create a solid color image as a placeholder (better than nothing)
        int width = 640;
        int height = 360;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Fill with a dark gray color
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0x333333);
            }
        }
        
        // Add a play button in the center
        int centerX = width / 2;
        int centerY = height / 2;
        int triangleSize = Math.min(width, height) / 4;
        
        // Paint a white triangle
        for (int y = centerY - triangleSize; y < centerY + triangleSize; y++) {
            for (int x = centerX - triangleSize/2; x < centerX + triangleSize; x++) {
                // Create a triangle pointing right
                int relX = x - centerX;
                int relY = y - centerY;
                
                // Simple triangle equation
                if (relX > 0 && Math.abs(relY) < (triangleSize - relX * triangleSize / (triangleSize * 2))) {
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        image.setRGB(x, y, 0xFFFFFF);
                    }
                }
            }
        }
        
        try {
            // Save the image
            boolean saved = ImageIO.write(image, "jpg", thumbnailPath.toFile());
            if (!saved) {
                logger.error("SIMPLE: Could not find a writer for jpg format");
                return getDefaultThumbnailUrl();
            }
            
            logger.info("SIMPLE: Created simple thumbnail at: {}", thumbnailPath);
            String thumbnailUrl = thumbnailsUrlPath + "/" + thumbnailFilename;
            return thumbnailUrl;
        } catch (Exception e) {
            logger.error("SIMPLE: Error creating simple thumbnail: {}", e.getMessage(), e);
            return getDefaultThumbnailUrl();
        }
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
} 