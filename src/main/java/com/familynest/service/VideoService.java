package com.familynest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.FilenameUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import java.util.UUID;

@Service
public class VideoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailDir;

    /**
     * Process a video file, save it to disk, and generate a thumbnail
     * @param file The video file to process
     * @return A map containing the video and thumbnail URLs
     */
    public VideoProcessingResult processVideo(MultipartFile file) throws IOException {
        // Create directories if they don't exist
        createDirectoriesIfNeeded();
        
        // Generate a unique filename to avoid collisions
        String timestamp = String.valueOf(System.currentTimeMillis());
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename);
        
        // Use timestamp for uniqueness
        String videoFilename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        String thumbnailFilename = timestamp + "_thumb_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        
        // Save the video file
        Path videoPath = Paths.get(uploadDir, videoFilename);
        file.transferTo(videoPath.toFile());
        
        // Generate thumbnail using FFmpeg
        Path thumbnailPath = generateThumbnail(videoPath.toString(), thumbnailFilename);
        
        // Create relative paths for web URLs
        String videoUrl = "/uploads/" + videoFilename;
        String thumbnailUrl = "/uploads/thumbnails/" + thumbnailFilename;
        
        return new VideoProcessingResult(videoUrl, thumbnailUrl);
    }
    
    /**
     * Generate a thumbnail from a video file using FFmpeg
     * @param videoPath The path to the video file
     * @param thumbnailFilename The filename for the thumbnail
     * @return The path to the generated thumbnail
     */
    private Path generateThumbnail(String videoPath, String thumbnailFilename) throws IOException {
        Path thumbnailPath = Paths.get(thumbnailDir, thumbnailFilename);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            
            // Seek to 1 second or 10% of the video, whichever is less
            long duration = grabber.getLengthInTime() / 1000; // in seconds
            long seekTime = Math.min(duration / 10, 1); // 10% of duration or 1 second
            grabber.setVideoTimestamp(seekTime * 1000000); // microseconds
            
            // Grab a frame for the thumbnail
            Frame frame = grabber.grabImage();
            if (frame == null) {
                // If seeking failed, try grabbing the first frame
                grabber.setVideoTimestamp(0);
                frame = grabber.grabImage();
                
                if (frame == null) {
                    throw new IOException("Could not grab any frame from video");
                }
            }
            
            // Convert frame to BufferedImage
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bufferedImage = converter.convert(frame);
            
            // Save the thumbnail
            ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
            
            grabber.stop();
            return thumbnailPath;
        } catch (Exception e) {
            throw new IOException("Failed to generate thumbnail: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create the upload and thumbnail directories if they don't exist
     */
    private void createDirectoriesIfNeeded() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Path thumbnailPath = Paths.get(thumbnailDir);
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        if (!Files.exists(thumbnailPath)) {
            Files.createDirectories(thumbnailPath);
        }
    }
    
    /**
     * A simple result object for video processing
     */
    public static class VideoProcessingResult {
        private String videoUrl;
        private String thumbnailUrl;
        
        public VideoProcessingResult(String videoUrl, String thumbnailUrl) {
            this.videoUrl = videoUrl;
            this.thumbnailUrl = thumbnailUrl;
        }
        
        public String getVideoUrl() {
            return videoUrl;
        }
        
        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }
} 