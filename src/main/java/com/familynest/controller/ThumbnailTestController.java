package com.familynest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple test controller that returns objects with thumbnailUrl
 * to test if the Flutter app can handle them
 */
@RestController
@RequestMapping("/test") // Use simple /test root path that's easy to access
@CrossOrigin(origins = "*") // Allow access from any origin
public class ThumbnailTestController {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailTestController.class);

    // Sample video URL that actually works (Big Buck Bunny)
    private static final String SAMPLE_VIDEO_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    
    // Sample thumbnail URL for this video
    private static final String SAMPLE_THUMBNAIL_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg";
    
    // Local video file from uploads
    private static final String LOCAL_VIDEO_PATH = "/uploads/1747080963737_62.mp4";
    
    // Local thumbnail file
    private static final String LOCAL_THUMBNAIL_PATH = "/uploads/thumbnails/1747145276033_4dcb2689_thumb.jpg";

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testThumbnail() {
        logger.info("Thumbnail test endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        
        // Create a test message with thumbnailUrl in camelCase
        Map<String, Object> testMessage = new HashMap<>();
        testMessage.put("id", 999L);
        testMessage.put("content", "Test video with thumbnail");
        testMessage.put("mediaType", "video");
        testMessage.put("mediaUrl", SAMPLE_VIDEO_URL);
        testMessage.put("thumbnailUrl", SAMPLE_THUMBNAIL_URL);
        
        // Add another test message with thumbnailUrl as absolute URL
        Map<String, Object> testMessage2 = new HashMap<>();
        testMessage2.put("id", 998L);
        testMessage2.put("content", "Another test video");
        testMessage2.put("mediaType", "video");
        testMessage2.put("mediaUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4");
        testMessage2.put("thumbnailUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg");
        
        // Create a list of test messages
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(testMessage);
        messages.add(testMessage2);
        
        // Add the messages to the response
        response.put("messages", messages);
        
        logger.info("Returning test messages with thumbnailUrl: {}", messages);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/videos")
    public ResponseEntity<Map<String, Object>> getVideoMessages() {
        logger.info("Video messages test endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        
        // Create test message with various forms of thumbnail URLs
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Message 1: Use camelCase thumbnailUrl - with working video URL
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("id", 1001L);
        msg1.put("content", "Test video with camelCase thumbnailUrl");
        msg1.put("mediaType", "video");
        msg1.put("mediaUrl", SAMPLE_VIDEO_URL);
        msg1.put("thumbnailUrl", SAMPLE_THUMBNAIL_URL);
        messages.add(msg1);
        
        // Message 2: Use snake_case thumbnail_url - with different working video
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("id", 1002L);
        msg2.put("content", "Test video with snake_case thumbnail_url");
        msg2.put("mediaType", "video");
        msg2.put("mediaUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
        msg2.put("thumbnail_url", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg");
        messages.add(msg2);
        
        // Message 3: Use both forms - with another working video
        Map<String, Object> msg3 = new HashMap<>();
        msg3.put("id", 1003L);
        msg3.put("content", "Test video with both thumbnail forms");
        msg3.put("mediaType", "video");
        msg3.put("mediaUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4");
        msg3.put("thumbnail_url", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg");
        msg3.put("thumbnailUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg");
        messages.add(msg3);
        
        // Message 4: Default thumbnail - with another sample video
        Map<String, Object> msg4 = new HashMap<>();
        msg4.put("id", 1004L);
        msg4.put("content", "Test video with relative path thumbnail");
        msg4.put("mediaType", "video");
        msg4.put("mediaUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4");
        msg4.put("thumbnailUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg");
        messages.add(msg4);
        
        response.put("messages", messages);
        response.put("totalElements", messages.size());
        
        logger.info("Returning {} test video messages with various thumbnail formats", messages.size());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/local-video")
    public ResponseEntity<Map<String, Object>> getLocalVideoMessage() {
        logger.info("Local video test endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Use an actual video and thumbnail from the uploads folder
        Map<String, Object> localVideoMsg = new HashMap<>();
        localVideoMsg.put("id", 2001L);
        localVideoMsg.put("content", "Local video with real thumbnail");
        localVideoMsg.put("mediaType", "video");
        localVideoMsg.put("mediaUrl", LOCAL_VIDEO_PATH);
        localVideoMsg.put("thumbnailUrl", LOCAL_THUMBNAIL_PATH);
        messages.add(localVideoMsg);
        
        response.put("messages", messages);
        response.put("totalElements", messages.size());
        
        logger.info("Returning local video test message with thumbnail: {}", localVideoMsg);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-image")
    public ResponseEntity<String> getTestImageHtml() {
        logger.info("Test image HTML endpoint called");
        
        // Create a simple HTML page with video and thumbnail images
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Thumbnail Test</title></head><body>");
        html.append("<h1>Thumbnail Test</h1>");
        
        // Test with various thumbnail formats
        html.append("<div style='margin: 20px;'>");
        html.append("<h2>1. Big Buck Bunny</h2>");
        html.append("<img src='" + SAMPLE_THUMBNAIL_URL + "' alt='Big Buck Bunny Thumbnail' />");
        html.append("</div>");
        
        html.append("<div style='margin: 20px;'>");
        html.append("<h2>2. Elephant's Dream</h2>");
        html.append("<img src='https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg' alt='Elephant's Dream Thumbnail' />");
        html.append("</div>");
        
        html.append("<div style='margin: 20px;'>");
        html.append("<h2>3. Local Thumbnail</h2>");
        html.append("<img src='" + LOCAL_THUMBNAIL_PATH + "' alt='Local Thumbnail' />");
        html.append("</div>");
        
        html.append("</body></html>");
        
        return ResponseEntity.ok()
            .header("Content-Type", "text/html")
            .body(html.toString());
    }
} 