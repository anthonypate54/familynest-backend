package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller for serving media files publicly without authentication.
 * This is crucial for thumbnails that need to be accessed by the mobile app.
 */
@RestController
@RequestMapping("/public/media")
@CrossOrigin(origins = "*")
public class PublicMediaController {
    
    private static final Logger logger = LoggerFactory.getLogger(PublicMediaController.class);
    
    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;
    
    /**
     * Serve thumbnail images without requiring authentication
     */
    @GetMapping("/thumbnails/{filename:.+}")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable String filename) {
        logger.info("Public request for thumbnail: {}", filename);
        try {
            Path filePath = Paths.get(uploadDir, "thumbnails").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
            } else {
                logger.warn("Thumbnail not found: {}", filename);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            logger.error("Error serving thumbnail: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Handle direct access to video files for public sharing
     */
    @GetMapping("/videos/{filename:.+}")
    public ResponseEntity<Resource> serveVideo(@PathVariable String filename) {
        logger.info("Serving video: {}", filename);
        try {
            Path filePath = Paths.get(uploadDir, "videos").resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("video/mp4"))
                    .body(resource);
            } else {
                logger.warn("Video not found: {}", filename);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            logger.error("Error serving video: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
} 