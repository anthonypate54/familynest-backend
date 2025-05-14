package com.example.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/fixed")
public class FixedController {

    private static final Logger logger = LoggerFactory.getLogger(FixedController.class);

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> postMessage(
        @PathVariable Long id,
        @RequestParam(value = "content", required = false) String content,
        @RequestPart(value = "media", required = false) MultipartFile media,
        @RequestParam(value = "mediaType", required = false) String mediaType,
        @RequestParam(value = "familyId", required = false) String familyIdStr) {
        logger.debug("Received request to post message for user ID: {}", id);
        logger.debug("Content: {}, Media: {}, MediaType: {}, FamilyId: {}", 
                    content, 
                    media != null ? media.getOriginalFilename() + " (" + media.getSize() + " bytes)" : "null", 
                    mediaType,
                    familyIdStr);
        
        try {
            logger.debug("Step 1: Finding user with ID: {}", id);
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            
            // Rest of the method remains unchanged...
            // This is the fix for the Flutter photo upload issue - changing @RequestPart to @RequestParam
            // for text fields, which matches how Flutter is building the multipart request.
        }
        catch (Exception e) {
            // Exception handling...
            return ResponseEntity.status(500).build();
        }
    }
} 