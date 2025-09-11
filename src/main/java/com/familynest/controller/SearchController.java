package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Simple test endpoint to verify the controller is working
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint(HttpServletRequest request) {
        logger.error("🔍 SEARCH TEST: Test endpoint called");
        logger.error("🔍 SEARCH TEST: Request URI: {}", request.getRequestURI());
        logger.error("🔍 SEARCH TEST: Request method: {}", request.getMethod());
        
        // Log all request attributes
        logger.error("🔍 SEARCH TEST: Request attributes:");
        java.util.Enumeration<String> attributeNames = request.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            Object value = request.getAttribute(name);
            logger.error("  {}: {}", name, value);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Search controller is working");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Search messages within user's families
     * Requires minimum 3 characters for search
     */
    @GetMapping("/messages")
    // DEBUG: Track endpoint hits
    public ResponseEntity<List<Map<String, Object>>> searchMessages(
            @RequestParam String q,
            @RequestParam(required = false) Long familyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        logger.error("🔍 SEARCH: Received search request for q='{}', familyId={}, page={}, size={}", q, familyId, page, size);
        logger.error("🔍 SEARCH: Request URI: {}", request.getRequestURI());
        logger.error("🔍 SEARCH: Request method: {}", request.getMethod());
        
        // Log all request attributes
        logger.error("🔍 SEARCH: Request attributes:");
        java.util.Enumeration<String> attributeNames = request.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            Object value = request.getAttribute(name);
            logger.error("  {}: {}", name, value);
        }
        
        try {
            // Get user ID from request attributes (set by AuthFilter)
            Long userId = (Long) request.getAttribute("userId");
            logger.error("🔍 SEARCH: Extracted userId from request attributes: {}", userId);
            
            if (userId == null) {
                logger.error("❌ SEARCH: No user ID found in request attributes - authentication failed");
                return ResponseEntity.status(401).body(List.of());
            }

            // Check minimum query length
            if (q.length() < 3) {
                logger.debug("Query too short ({} chars), minimum 3 required", q.length());
                return ResponseEntity.ok(List.of());
            }

            // Build the search query
            String sql = buildSearchQuery(familyId != null);
            Object[] params = buildSearchParams(userId, q, familyId, page, size);
            
            logger.debug("Executing search query with {} parameters", params.length);
            
            // Add debug logging for DM search
            if (familyId == null) {
                logger.debug("🔍 SEARCH: Searching for '{}' in both family messages and DM messages", q);
                
                // Test DM search separately
                String dmTestSql = """
                    SELECT COUNT(*) as dm_count
                    FROM dm_message dm
                    JOIN dm_conversation dc ON dm.conversation_id = dc.id
                    WHERE (dc.user1_id = ? OR dc.user2_id = ?)
                    AND LOWER(dm.content) LIKE LOWER(?)
                    """;
                
                try {
                    Integer dmCount = jdbcTemplate.queryForObject(dmTestSql, Integer.class, userId, userId, "%" + q + "%");
                    logger.debug("🔍 SEARCH: Found {} DM messages containing '{}'", dmCount, q);
                } catch (Exception e) {
                    logger.debug("🔍 SEARCH: Error testing DM search: {}", e.getMessage());
                }
            }
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params);
            
            logger.debug("Search returned {} results", results.size());
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    /**
     * Get user's families for search filter dropdown
     */
    @GetMapping("/families")
    public ResponseEntity<List<Map<String, Object>>> getUserFamilies(
            HttpServletRequest request) {
        
        logger.error("🔍 FAMILIES: Getting user families for search filter");
        logger.error("🔍 FAMILIES: Request URI: {}", request.getRequestURI());
        logger.error("🔍 FAMILIES: Request method: {}", request.getMethod());
        
        // Log all request attributes
        logger.error("🔍 FAMILIES: Request attributes:");
        java.util.Enumeration<String> attributeNames = request.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            Object value = request.getAttribute(name);
            logger.error("  {}: {}", name, value);
        }
        
        try {
            Long userId = (Long) request.getAttribute("userId");
            logger.error("🔍 FAMILIES: Extracted userId from request attributes: {}", userId);
            
            if (userId == null) {
                logger.error("❌ FAMILIES: No user ID found in request attributes - authentication failed");
                return ResponseEntity.status(401).body(List.of());
            }

            String sql = """
                SELECT DISTINCT f.id as family_id, f.name as family_name
                FROM family f
                JOIN user_family_membership ufm ON f.id = ufm.family_id
                WHERE ufm.user_id = ?
                ORDER BY f.name
                """;
            
            List<Map<String, Object>> families = jdbcTemplate.queryForList(sql, userId);
            logger.debug("Found {} families for user {}", families.size(), userId);
            
            return ResponseEntity.ok(families);
            
        } catch (Exception e) {
            logger.error("Error getting user families: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    private String buildSearchQuery(boolean hasFamilyFilter) {
        if (hasFamilyFilter) {
            // When filtering by family, only search family messages
            return """
                WITH user_families AS (
                    SELECT ufm.family_id
                    FROM user_family_membership ufm
                    WHERE ufm.user_id = ?
                ),
                family_messages AS (
                    SELECT 
                        m.id,
                        m.content,
                        m.sender_id,
                        m.sender_username,
                        mfl.family_id,
                        m.timestamp,
                        m.media_type,
                        m.media_url,
                        m.thumbnail_url,
                        f.name as family_name,
                        s.first_name as sender_first_name,
                        s.last_name as sender_last_name,
                        s.photo as sender_photo,
                        'family' as message_type
                    FROM message m
                    JOIN message_family_link mfl ON m.id = mfl.message_id
                    JOIN user_families uf ON mfl.family_id = uf.family_id
                    JOIN family f ON mfl.family_id = f.id
                    LEFT JOIN app_user s ON m.sender_id = s.id
                    WHERE LOWER(m.content) LIKE LOWER(?)
                    AND mfl.family_id = ?
                    ORDER BY m.timestamp DESC
                    LIMIT ? OFFSET ?
                )
                SELECT * FROM family_messages
                """;
        } else {
            // Search only DM messages - simplified query
            return "WITH user_conversations AS (" +
                   "  SELECT DISTINCT id as conversation_id " +
                   "  FROM dm_conversation " +
                   "  WHERE user1_id = ? OR user2_id = ? " +
                   "), " +
                   "dm_messages AS (" +
                   "  SELECT " +
                   "    dm.id, " +
                   "    dm.content, " +
                   "    dm.sender_id, " +
                   "    u.username as sender_username, " +
                   "    NULL as family_id, " +
                   "    dm.created_at as timestamp, " +
                   "    dm.media_type, " +
                   "    dm.media_url, " +
                   "    dm.media_thumbnail as thumbnail_url, " +
                   "    'Direct Message' as family_name, " +
                   "    u.first_name as sender_first_name, " +
                   "    u.last_name as sender_last_name, " +
                   "    u.photo as sender_photo, " +
                   "    'dm' as message_type, " +
                   "    dm.conversation_id " +
                   "  FROM dm_message dm " +
                   "  JOIN user_conversations uc ON dm.conversation_id = uc.conversation_id " +
                   "  LEFT JOIN app_user u ON dm.sender_id = u.id " +
                   "  WHERE LOWER(dm.content) LIKE LOWER(?) " +
                   ") " +
                   "SELECT * FROM dm_messages " +
                   "ORDER BY timestamp DESC " +
                   "LIMIT ? OFFSET ?";
        }
    }

    private Object[] buildSearchParams(Long userId, String query, Long familyId, int page, int size) {
        int offset = page * size;
        
        if (familyId != null) {
            // Search only in specific family (no DM messages when filtering by family)
            return new Object[]{userId, "%" + query + "%", familyId, size, offset};
        } else {
            // Search only in DM messages (simplified)
            return new Object[]{userId, userId, "%" + query + "%", size, offset};
        }
    }
} 