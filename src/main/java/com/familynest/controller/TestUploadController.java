package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestUploadController {

    private static final Logger logger = LoggerFactory.getLogger(TestUploadController.class);

    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;

    @GetMapping("/photo-test")
    public ResponseEntity<Map<String, String>> testPhotoEndpoint() {
        logger.debug("Photo test endpoint called");
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "active");
        response.put("uploadDirectory", uploadDir);
        
        // Check if upload directory exists and is writable
        Path uploadPath = Paths.get(uploadDir);
        boolean dirExists = Files.exists(uploadPath);
        boolean dirWritable = Files.isWritable(uploadPath);
        
        response.put("directoryExists", String.valueOf(dirExists));
        response.put("directoryWritable", String.valueOf(dirWritable));
        
        if (!dirExists) {
            try {
                Files.createDirectories(uploadPath);
                response.put("directoryCreated", "true");
            } catch (Exception e) {
                response.put("directoryCreationError", e.getMessage());
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/upload-test")
    public ResponseEntity<Map<String, String>> testPhotoUpload(
            @RequestPart(value = "photo", required = true) MultipartFile photo) {
        logger.debug("Test photo upload initiated");
        
        Map<String, String> response = new HashMap<>();
        
        try {
            if (photo != null && !photo.isEmpty()) {
                String fileName = System.currentTimeMillis() + "_test_" + photo.getOriginalFilename();
                Path filePath = Paths.get(uploadDir, fileName);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, photo.getBytes());
                
                response.put("status", "success");
                response.put("fileName", fileName);
                response.put("fileSize", String.valueOf(photo.getSize()));
                response.put("contentType", photo.getContentType());
                response.put("photoUrl", "/api/users/photos/" + fileName);
                
                logger.debug("Test photo uploaded successfully: {}", fileName);
            } else {
                response.put("status", "error");
                response.put("message", "Empty file");
                logger.debug("Test photo upload failed: empty file");
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            logger.error("Test photo upload error: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.ok(response);
    }
} 