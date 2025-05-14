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
            
            // Use a CompletableFuture with timeout to prevent hanging
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return generateThumbnailInternal(videoPath, thumbnailFilename);
                } catch (Exception e) {
                    logger.error("THUMBNAIL SERVICE: Error generating thumbnail: {}", e.getMessage(), e);
                    return null;
                }
            }, executor);
            
            // Wait for completion with timeout
            String result = future.get(THUMBNAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.error("THUMBNAIL SERVICE: Generation completed, result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("THUMBNAIL SERVICE: Failed to generate thumbnail with timeout: {}", e.getMessage(), e);
            return null;
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
        
        logger.info("Generating thumbnail for video: {}", videoPath);
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        logger.info("Thumbnail will be saved to: {}", thumbnailPath);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            logger.info("Starting FFmpegFrameGrabber for video: {}", videoPath);
            
            try {
                grabber.start();
                
                // Get video duration in seconds
                long durationInSeconds = Math.max(1, grabber.getLengthInTime() / 1000000);
                logger.debug("Video duration: {} seconds", durationInSeconds);
                
                // Seek to 1 second or 10% of the video, whichever is less
                long seekTime = Math.min(durationInSeconds / 10, 1);
                logger.debug("Seeking to {} seconds for thumbnail", seekTime);
                grabber.setVideoTimestamp(seekTime * 1000000);
                
                // Grab a frame for the thumbnail
                logger.info("Grabbing frame from video for thumbnail");
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    // If seeking failed, try grabbing the first frame
                    logger.warn("Could not grab frame at {} seconds, trying first frame", seekTime);
                    grabber.setVideoTimestamp(0);
                    frame = grabber.grabImage();
                    
                    if (frame == null) {
                        logger.error("Could not grab any frame from video");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                }
                
                // Convert frame to BufferedImage
                logger.info("Converting frame to BufferedImage");
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage bufferedImage = converter.convert(frame);
                
                if (bufferedImage == null) {
                    logger.error("Failed to convert frame to BufferedImage");
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                
                // Save the thumbnail
                logger.info("Saving thumbnail to: {}", thumbnailPath);
                try {
                    boolean saved = ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
                    if (!saved) {
                        logger.error("ImageIO could not find a writer for format: jpg");
                        return "/uploads/thumbnails/default_thumbnail.jpg";
                    }
                    logger.info("Thumbnail generated successfully: {}", thumbnailPath);
                } catch (Exception e) {
                    logger.error("Failed to save thumbnail image: {}", e.getMessage(), e);
                    return "/uploads/thumbnails/default_thumbnail.jpg";
                }
                
                // Return URL path for the thumbnail
                String thumbnailUrl = "/uploads/thumbnails/" + thumbnailFilename;
                logger.info("Returning thumbnail URL: {}", thumbnailUrl);
                return thumbnailUrl;
            } catch (Exception e) {
                logger.error("Error processing video: {}", e.getMessage(), e);
                try {
                    grabber.stop();
                } catch (Exception stopEx) {
                    logger.error("Error stopping grabber: {}", stopEx.getMessage());
                }
                return "/uploads/thumbnails/default_thumbnail.jpg";
            }
        } catch (Exception e) {
            logger.error("Error in thumbnail generation: {}", e.getMessage(), e);
            return "/uploads/thumbnails/default_thumbnail.jpg";
        }
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