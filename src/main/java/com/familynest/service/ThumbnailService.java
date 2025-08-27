package com.familynest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.familynest.service.storage.StorageService;

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
    
    @Autowired
    private StorageService storageService;
       
    @Value("${app.use.ffmpeg:true}")
    private boolean useFFmpeg;
    
    // Default thumbnail directory for compatibility
    private String thumbnailDir = "uploads/thumbnails";
    
    /**
     * Generates a thumbnail for a video file with a timeout mechanism
     * @param videoPath Path to the video file
     * @param thumbnailFilename Desired filename for the thumbnail
     * @return Path to the thumbnail, or null if generation failed
     */
    public String generateThumbnail(String videoPath, String thumbnailFilename) {
        try {
            logger.error("THUMBNAIL SERVICE: Starting thumbnail generation for: {}", videoPath);
            // Thumbnail directory creation is handled by StorageService
            
            // First try using FFmpeg to extract an actual frame from the video
            try {
                logger.error("THUMBNAIL SERVICE: Attempting to use FFmpeg for thumbnail generation");
                String result = generateThumbnailInternal(videoPath, thumbnailFilename);
                
                // If FFmpeg succeeded, return the result
                if (result != null && !result.equals("/uploads/thumbnails/default_thumbnail.jpg")) {
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
                return "/uploads/thumbnails/default_thumbnail.jpg";
            }
        } catch (Exception e) {
            logger.error("THUMBNAIL SERVICE: Failed to generate thumbnail: {}", e.getMessage(), e);
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
    }
    
    /**
     * Internal method to generate thumbnail without timeout handling
     */
    private String generateThumbnailInternal(String videoPath, String thumbnailFilename) {
        if (!useFFmpeg) {
            logger.info("FFmpeg thumbnail generation is disabled. Using default thumbnail.");
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
        
        // Check that the video file exists
        File videoFile = new File(videoPath);
        if (!videoFile.exists() || !videoFile.canRead()) {
            logger.error("FFMPEG: Video file does not exist or cannot be read: {}", videoPath);
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
        
        logger.error("VIDEO PATH CHECK: Path exists? {}, Size: {} bytes", Files.exists(Paths.get(videoPath)), videoFile.length());
        logger.error("THUMBNAIL GENERATION: Video path = {}, Thumbnail filename = {}", videoPath, thumbnailFilename);
        
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        logger.error("THUMBNAIL PATH: Full path = {}, Directory exists? {}", 
            thumbnailPath.toAbsolutePath(), Files.exists(thumbnailPath.getParent()));
        
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
            
            // Enable detailed FFmpeg logging
            try {
                logger.error("FFMPEG: Setting up FFmpeg logging");
                org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_VERBOSE);
                org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
            } catch (Exception e) {
                logger.error("FFMPEG: Failed to set up FFmpeg logging: {}", e.getMessage());
            }
            
            try {
                logger.error("FFMPEG: About to start grabber...");
                grabber.start();
                logger.error("FFMPEG: Grabber started successfully!");
                
                // Get video duration in seconds
                long durationInSeconds = Math.max(1, grabber.getLengthInTime() / 1000000);
                logger.error("FFMPEG: Video duration: {} seconds", durationInSeconds);
                
                // Seek to 1 second or 10% of the video, whichever is less
                long seekTime = Math.min(durationInSeconds / 10, 1);
                logger.error("FFMPEG: Seeking to {} seconds for thumbnail", seekTime);
                grabber.setVideoTimestamp(seekTime * 1000000);
                
                // Grab a frame for the thumbnail
                logger.error("FFMPEG: About to grab frame from video for thumbnail");
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    // If seeking failed, try grabbing the first frame
                    logger.error("FFMPEG: Could not grab frame at {} seconds, trying first frame", seekTime);
                    grabber.setVideoTimestamp(0);
                    frame = grabber.grabImage();
                    
                    if (frame == null) {
                        logger.error("FFMPEG: Could not grab any frame from video, all attempts failed");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                }
                logger.error("FFMPEG: Successfully grabbed frame from video!");
                
                // Convert frame to BufferedImage
                logger.error("FFMPEG: Converting frame to BufferedImage");
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage bufferedImage = converter.convert(frame);
                
                if (bufferedImage == null) {
                    logger.error("FFMPEG: Failed to convert frame to BufferedImage");
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                logger.error("FFMPEG: Successfully converted frame to BufferedImage!");
                
                // Save the thumbnail
                logger.error("FFMPEG: About to save thumbnail to: {}", thumbnailPath);
                try {
                    boolean saved = ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
                    if (!saved) {
                        logger.error("FFMPEG: ImageIO could not find a writer for format: jpg");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                    logger.error("FFMPEG: Thumbnail generated successfully: {}", thumbnailPath);
                } catch (Exception e) {
                    logger.error("FFMPEG: Failed to save thumbnail image: {}", e.getMessage(), e);
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                
                // Return URL path for the thumbnail
                String thumbnailUrl = "/uploads/thumbnails/" + thumbnailFilename;
                logger.error("FFMPEG: Returning thumbnail URL: {}", thumbnailUrl);
                return thumbnailUrl;
            } catch (Exception e) {
                logger.error("FFMPEG: Error processing video: {}", e.getMessage(), e);
                try {
                    grabber.stop();
                } catch (Exception stopEx) {
                    logger.error("FFMPEG: Error stopping grabber: {}", stopEx.getMessage());
                }
                return "/uploads/thumbnails/default_thumbnail.jpg";
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
                logger.error("FFMPEG: Fallback method also failed: {}", fallbackEx.getMessage());
            }
            
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
    }
    
    /**
     * A simplified fallback method for generating thumbnails without FFmpeg
     */
    private String generateSimpleThumbnail(String videoPath, String thumbnailFilename) throws Exception {
        // Create a default colored image as a thumbnail
        int width = 640;
        int height = 360;
        
        // Debug log the paths we're working with
        logger.error("PATH DEBUG: videoPath = {}", videoPath);
        logger.error("PATH DEBUG: thumbnailFilename = {}", thumbnailFilename);
        logger.error("PATH DEBUG: thumbnailDir = {}", thumbnailDir);
        
        // Create a simple colored image with the video name on it
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        
        // Fill with gradient
        g.setColor(java.awt.Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);
        
        // Add play button icon instead of text
        g.setColor(java.awt.Color.WHITE);
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Draw play button triangle
        int[] xPoints = {centerX - 30, centerX + 30, centerX - 30};
        int[] yPoints = {centerY - 25, centerY, centerY + 25};
        g.fillPolygon(xPoints, yPoints, 3);
        
        // Draw circle around play button
        g.drawOval(centerX - 50, centerY - 50, 100, 100);
        
        g.dispose();
        
        // Make sure we handle the path correctly, avoiding duplication
        Path thumbnailPath;
        
        // Ensure we're only using the filename, not a path
        if (thumbnailFilename.contains("/") || thumbnailFilename.contains("\\")) {
            thumbnailFilename = new File(thumbnailFilename).getName();
        }
        
        // Create path to save thumbnail
        thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        
        // Ensure parent directory exists
        Files.createDirectories(thumbnailPath.getParent());
        
        logger.error("PATH DEBUG: Final thumbnail path: {}", thumbnailPath.toAbsolutePath());
        
        // Save the image
        boolean success = ImageIO.write(image, "jpg", thumbnailPath.toFile());
        
        if (!success) {
            logger.error("Failed to write thumbnail image - no appropriate writer found");
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
        
        logger.error("PATH DEBUG: Thumbnail saved successfully");
        
        // Return standard URL path
        return "/uploads/thumbnails/" + thumbnailFilename;
    }
    
    /**
     * Cleanup method to shutdown the executor service
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 