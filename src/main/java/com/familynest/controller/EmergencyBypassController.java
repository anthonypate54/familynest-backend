package com.familynest.controller;

import com.familynest.model.User;
import com.familynest.repository.UserRepository;
import com.familynest.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Emergency bypass controller for accessing user data during troubleshooting
 * TEMPORARY - Remove after authentication issues are resolved
 */
@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class EmergencyBypassController {

    private static final Logger logger = LoggerFactory.getLogger(EmergencyBypassController.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/users/101")
    public ResponseEntity<Map<String, Object>> getUser101() {
        logger.error("🚨 EMERGENCY BYPASS: Getting user 101");
        
        try {
            // Use a single optimized SQL query that returns everything in one go
            String sql = "WITH user_data AS (" +
                        "  SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.role, u.photo " +
                        "  FROM app_user u WHERE u.id = 101 " +
                        "), " +
                        "membership_data AS (" +
                        "  SELECT ufm.family_id, ufm.role as membership_role, ufm.is_active, " +
                        "         f.name as family_name, f.created_by as family_created_by " +
                        "  FROM user_family_membership ufm " +
                        "  JOIN family f ON ufm.family_id = f.id " +
                        "  WHERE ufm.user_id = 101 " +
                        "  ORDER BY ufm.is_active DESC, ufm.id ASC " +
                        "  LIMIT 1 " +
                        ") " +
                        "SELECT ud.*, md.family_id, md.family_name, " +
                        "       md.membership_role, md.is_active, " +
                        "       (md.family_created_by = ud.id) as is_family_owner " +
                        "FROM user_data ud " +
                        "LEFT JOIN membership_data md ON 1=1";
            
            logger.error("🚨 EMERGENCY BYPASS: Executing optimized query for user 101");
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (results.isEmpty()) {
                logger.error("🚨 EMERGENCY BYPASS: User 101 not found");
                return ResponseEntity.notFound().build();
            }
            
            // Transform the results into a response
            Map<String, Object> userData = results.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("id", userData.get("id"));
            response.put("username", userData.get("username"));
            response.put("firstName", userData.get("first_name"));
            response.put("lastName", userData.get("last_name"));
            response.put("email", userData.get("email"));
            response.put("role", userData.get("role") != null ? userData.get("role") : "USER");
            response.put("photo", userData.get("photo"));
            response.put("familyId", userData.get("family_id"));
            
            logger.error("🚨 EMERGENCY BYPASS: Returning user 101 data");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("🚨 EMERGENCY BYPASS: Error getting user 101", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/101/messages")
    public ResponseEntity<List<Map<String, Object>>> getUser101Messages() {
        logger.error("🚨 EMERGENCY BYPASS: Getting messages for user 101");
        
        try {
            String sql = "WITH message_subset AS (" +
                        "  SELECT m.id " +
                        "  FROM message m " +
                        "  JOIN user_family_membership ufm ON m.family_id = ufm.family_id " +
                        "  WHERE ufm.user_id = 101 " +
                        "  ORDER BY m.timestamp DESC " +
                        "  LIMIT 50" +
                        ") " +
                        "SELECT " +
                        "  m.id, m.content, m.sender_username, m.sender_id, m.family_id, " +
                        "  m.timestamp, m.media_type, m.media_url, m.thumbnail_url, " +
                        "  s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                        "  f.name as family_name " +
                        "FROM message m " +
                        "JOIN message_subset ms ON m.id = ms.id " +
                        "LEFT JOIN app_user s ON m.sender_id = s.id " +
                        "LEFT JOIN family f ON m.family_id = f.id " +
                        "ORDER BY m.timestamp DESC ";

            logger.error("🚨 EMERGENCY BYPASS: Executing messages query for user 101");
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            List<Map<String, Object>> response = results.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", message.get("id"));
                messageMap.put("content", message.get("content"));
                messageMap.put("senderUsername", message.get("sender_username"));
                messageMap.put("senderId", message.get("sender_id"));
                messageMap.put("senderPhoto", message.get("sender_photo"));
                messageMap.put("senderFirstName", message.get("sender_first_name"));
                messageMap.put("senderLastName", message.get("sender_last_name"));
                messageMap.put("familyId", message.get("family_id"));
                messageMap.put("familyName", message.get("family_name"));
                messageMap.put("timestamp", message.get("timestamp") != null ? message.get("timestamp").toString() : null);
                messageMap.put("mediaType", message.get("media_type"));
                messageMap.put("mediaUrl", message.get("media_url"));
                messageMap.put("thumbnailUrl", message.get("thumbnail_url"));
                
                messageMap.put("viewCount", 0);
                messageMap.put("reactionCount", 0);
                messageMap.put("commentCount", 0);
                
                return messageMap;
            }).collect(Collectors.toList());
            
            logger.error("🚨 EMERGENCY BYPASS: Returning {} messages for user 101", response.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("🚨 EMERGENCY BYPASS: Error getting messages for user 101", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }
} 