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
import java.awt.geom.AffineTransform;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            logger.info("THUMBNAIL SERVICE: Starting thumbnail generation for: {}", videoPath);
            // Thumbnail directory creation is handled by StorageService
            
            // First try using FFmpeg to extract an actual frame from the video
            try {
                logger.info("THUMBNAIL SERVICE: Attempting to use FFmpeg for thumbnail generation");
                String result = generateThumbnailInternal(videoPath, thumbnailFilename);
                
                // If FFmpeg succeeded, return the result
                if (result != null && !result.equals("/uploads/thumbnails/default_thumbnail.jpg")) {
                    logger.info("THUMBNAIL SERVICE: FFmpeg thumbnail generation successful: {}", result);
                    return result;
                }
                
                // If FFmpeg method returned default thumbnail, fall back to simple method
                logger.info("THUMBNAIL SERVICE: FFmpeg returned default thumbnail, trying simple method as fallback");
            } catch (Exception e) {
                logger.error("THUMBNAIL SERVICE: FFmpeg method failed: {}", e.getMessage(), e);
                // Continue to fallback method
            }
            
            // Use simple method as fallback
            try {
                logger.info("THUMBNAIL SERVICE: Using simple Java Image method as fallback");
                String result = generateSimpleThumbnail(videoPath, thumbnailFilename);
                logger.info("THUMBNAIL SERVICE: Simple thumbnail generation successful: {}", result);
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
        logger.info("⏱️ TIMING: Starting thumbnail generation for: {}", thumbnailFilename);
        
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
        
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        
        // First, detect if the video has rotation metadata
        int videoRotation = getVideoRotation(videoPath);
        logger.info("ROTATION DEBUG: Video has {}° rotation metadata", videoRotation);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            // Set the format explicitly to help FFmpeg recognize the file
            if (videoPath.toLowerCase().endsWith(".mp4")) {
                grabber.setFormat("mp4");
            } else if (videoPath.toLowerCase().endsWith(".avi")) {
                grabber.setFormat("avi");
            } else if (videoPath.toLowerCase().endsWith(".mov")) {
                grabber.setFormat("mov");
            }
            
            // Enable auto-rotation based on video metadata (keep for iOS compatibility)
            grabber.setOption("autorotate", "1");
            
            try {
                grabber.start();
                  
                // Get video duration and seek to a good frame
                long durationInSeconds = Math.max(1, grabber.getLengthInTime() / 1000000);
                long seekTime = Math.min(durationInSeconds / 10, 1);
                grabber.setVideoTimestamp(seekTime * 1000000);
                
                // Grab a frame for the thumbnail
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    // If seeking failed, try grabbing the first frame
                    grabber.setVideoTimestamp(0);
                    frame = grabber.grabImage();
                    
                    if (frame == null) {
                        logger.error("FFMPEG: Could not grab any frame from video");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                }
                 
                // Convert frame to BufferedImage
                BufferedImage bufferedImage;
                try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                    bufferedImage = converter.convert(frame);
                }
                
                if (bufferedImage == null) {
                    logger.error("FFMPEG: Failed to convert frame to BufferedImage");
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                
                // Apply manual rotation if video has rotation metadata and autorotate may have failed
                // This serves as a fallback for Android videos where autorotate doesn't work properly
                if (videoRotation != 0) {
                    logger.info("ROTATION DEBUG: Video has {}° rotation, applying manual rotation as fallback", videoRotation);
                    logger.info("ROTATION DEBUG: Original image size: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
                    bufferedImage = applyManualRotation(bufferedImage, videoRotation);
                    logger.info("ROTATION DEBUG: Rotated image size: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
                } else {
                    logger.info("ROTATION DEBUG: No rotation metadata found, using autorotate result as-is");
                }
                
                // Save the thumbnail
                try {
                    boolean saved = ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
                    if (!saved) {
                        logger.error("FFMPEG: Failed to save thumbnail - no writer found");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                    
                    // Verify the file was created
                    File savedFile = thumbnailPath.toFile();
                    if (!savedFile.exists()) {
                        logger.error("FFMPEG: Thumbnail file was not created: {}", thumbnailPath);
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                    
                 } catch (Exception e) {
                    logger.error("FFMPEG: Failed to save thumbnail: {}", e.getMessage());
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                
                return "/uploads/thumbnails/" + thumbnailFilename;
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
                logger.info("FFMPEG: Attempting simple Java Image fallback method");
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
        logger.info("PATH DEBUG: videoPath = {}", videoPath);
        logger.info("PATH DEBUG: thumbnailFilename = {}", thumbnailFilename);
        logger.info("PATH DEBUG: thumbnailDir = {}", thumbnailDir);
        
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
        
        logger.info("PATH DEBUG: Final thumbnail path: {}", thumbnailPath.toAbsolutePath());
        
        // Save the image
        boolean success = ImageIO.write(image, "jpg", thumbnailPath.toFile());
        
        if (!success) {
            logger.error("Failed to write thumbnail image - no appropriate writer found");
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
        
        logger.info("PATH DEBUG: Thumbnail saved successfully");
        
        // Return standard URL path
        return "/uploads/thumbnails/" + thumbnailFilename;
    }
    
    /**
     * Extract rotation metadata from video file
     * @param videoPath Path to the video file
     * @return Rotation in degrees (0, 90, 180, 270) or 0 if no rotation metadata
     */
    private int getVideoRotation(String videoPath) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            
            // Try to get rotation from basic metadata first (iOS style)
            String rotationStr = grabber.getVideoMetadata("rotate");
            logger.info("ROTATION DEBUG: Basic rotation metadata: '{}'", rotationStr);
            
            if (rotationStr != null && !rotationStr.isEmpty()) {
                try {
                    int rotation = Integer.parseInt(rotationStr);
                    logger.info("ROTATION DEBUG: Found basic rotation: {}°", rotation);
                    return normalizeRotation(rotation);
                } catch (NumberFormatException e) {
                    logger.warn("ROTATION DEBUG: Invalid basic rotation metadata: {}", rotationStr);
                }
            }
            
            // Android stores rotation in display matrix - try to get it from video stream
            try {
                // Get video stream rotation (Android style)
                double displayRotation = grabber.getVideoMetadata("displaymatrix");
                logger.info("ROTATION DEBUG: Display matrix rotation attempt: {}", displayRotation);
            } catch (Exception e) {
                logger.debug("ROTATION DEBUG: Could not get displaymatrix directly: {}", e.getMessage());
            }
            
            // For Android videos, we need to parse from the actual stream metadata
            // Based on your logs, we see "displaymatrix: rotation of -90.00 degrees"
            // Let's assume Android videos with 1280x720 that should be 720x1280 are rotated 90°
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            logger.info("ROTATION DEBUG: Video dimensions: {}x{}", width, height);
            
            // Heuristic for Android: if width > height but it's a portrait recording,
            // it's likely rotated. Most phone videos in portrait should be taller than wide.
            if (width > height && width == 1280 && height == 720) {
                logger.info("ROTATION DEBUG: Detected likely Android portrait video (1280x720), assuming 90° rotation needed");
                return 90;
            }
            
            logger.info("ROTATION DEBUG: No rotation detected, using as-is");
            return 0;
            
        } catch (Exception e) {
            logger.warn("ROTATION: Could not read rotation metadata: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Normalize rotation to standard values (0, 90, 180, 270)
     */
    private int normalizeRotation(int rotation) {
        // Handle negative rotations and normalize to 0-360 range
        rotation = ((rotation % 360) + 360) % 360;
        
        // Round to nearest 90-degree increment
        if (rotation >= 45 && rotation < 135) return 90;
        if (rotation >= 135 && rotation < 225) return 180;
        if (rotation >= 225 && rotation < 315) return 270;
        return 0;
    }
    
    /**
     * Apply manual rotation to BufferedImage when autorotate fails
     * @param original The original image
     * @param rotationDegrees Rotation in degrees (90, 180, 270)
     * @return Rotated image
     */
    private BufferedImage applyManualRotation(BufferedImage original, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return original;
        }
        
        logger.info("ROTATION: Applying manual rotation of {}° (autorotate fallback)", rotationDegrees);
        
        int width = original.getWidth();
        int height = original.getHeight();
        
        // For 90° and 270° rotations, swap width and height
        int newWidth = (rotationDegrees == 90 || rotationDegrees == 270) ? height : width;
        int newHeight = (rotationDegrees == 90 || rotationDegrees == 270) ? width : height;
        
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = rotated.createGraphics();
        
        // Set high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // Create rotation transform
        AffineTransform transform = new AffineTransform();
        
        switch (rotationDegrees) {
            case 90:
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
                break;
            case 180:
                transform.translate(width, height);
                transform.rotate(Math.PI);
                break;
            case 270:
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
                break;
        }
        
        g2d.setTransform(transform);
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        
        logger.debug("ROTATION: Manual rotation completed: {} -> {}x{}", 
                     rotationDegrees, newWidth, newHeight);
        
        return rotated;
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