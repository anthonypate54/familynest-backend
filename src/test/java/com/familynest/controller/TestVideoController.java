package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
// Removed @Component to prevent duplicate bean registration
// import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;


public class TestVideoController extends VideoController {
    private static final Logger logger = LoggerFactory.getLogger(TestVideoController.class);

    // Override inherited @Value annotations with test defaults
    @Value("${file.upload-dir:/tmp/familynest-uploads-test}")
    private String uploadDir;
    
    @Value("${app.videos.dir:/tmp/familynest-uploads-test/videos}")
    private String videosDir;
    
    @Value("${app.thumbnail.dir:/tmp/familynest-uploads-test/thumbnails}")
    private String thumbnailDir;
    
    @Value("${app.url.videos:/uploads/videos}")
    private String videosUrlPath;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;

    @Override
    public ResponseEntity<Map<String, String>> uploadVideo(@RequestPart("file") MultipartFile file) {
        logger.info("TestVideoController: Handling video upload (stubbed for testing)");
        Map<String, String> response = new HashMap<>();
        response.put("videoUrl", "/uploads/videos/test_video.mp4");
        response.put("thumbnailUrl", "/uploads/thumbnails/test_thumbnail.jpg");
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.info("TestVideoController: Handling health check (stubbed for testing)");
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Video processing service is available (test stub)");
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, String>> testEndpoint() {
        logger.info("TestVideoController: Handling test endpoint (stubbed for testing)");
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("videoUrl", "/uploads/videos/test_video.mp4");
        response.put("thumbnailUrl", "/uploads/thumbnails/default_thumbnail.jpg");
        return ResponseEntity.ok(response);
    }

    @Override
    public String getThumbnailForVideo(String videoUrl) {
        logger.info("TestVideoController: Getting thumbnail for video (stubbed for testing)");
        return "/uploads/thumbnails/test_thumbnail.jpg";
    }
} 