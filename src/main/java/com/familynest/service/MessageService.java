package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.EmptyResultDataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getCommentById(Long commentId) {
        String sql = "SELECT " +
            "m.id, m.content, m.sender_username, m.sender_id, " +
            "m.timestamp, m.media_type, m.media_url, m.thumbnail_url, m.local_media_path, " +
            "s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
            "m.parent_message_id as parent_message_id, " +
            "m.like_count, m.love_count, " +
            "(SELECT COUNT(*) FROM message_comment WHERE parent_message_id = m.parent_message_id) as comment_count " +
            "FROM message_comment m " +
            "LEFT JOIN app_user s ON m.sender_id = s.id " +
            "WHERE m.id = ?";
        
        try {
            return jdbcTemplate.queryForMap(sql, commentId);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("Comment not found with ID: {}", commentId);
            throw new RuntimeException("Comment not found with ID: " + commentId);
        }
    }

    public Map<String, Object> getMessageById(Long messageId) {
        String sql = "SELECT m.id, m.content, m.sender_username, m.sender_id, mfl.family_id, " +
                     "m.timestamp, m.media_type, m.media_url, m.thumbnail_url, m.local_media_path, " +
                     "s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                     "f.name as family_name, " +
                     "m.like_count, m.love_count, " +
                     "(SELECT COUNT(*) FROM message_comment WHERE parent_message_id = m.id) as comment_count " +
                     "FROM message m " +
                     "LEFT JOIN message_family_link mfl ON m.id = mfl.message_id " +
                     "LEFT JOIN family f ON mfl.family_id = f.id " +
                     "LEFT JOIN app_user s ON m.sender_id = s.id " +
                     "WHERE m.id = ? " +
                     "LIMIT 1";
        
        try {
            return jdbcTemplate.queryForMap(sql, messageId);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("Message not found with ID: {} - this could be a race condition during WebSocket broadcasting", messageId);
            throw new RuntimeException("Message not found with ID: " + messageId);
        }
    }
}