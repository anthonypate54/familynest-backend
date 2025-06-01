package com.familynest.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MessageService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getCommentById(Long commentId) {
        String sql = "SELECT m.id, m.content, m.sender_username, m.sender_id, m.family_id, " +
                     "m.timestamp, m.media_type, m.media_url, m.thumbnail_url, " +
                     "s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                     "f.name as family_name, m.parent_message_id as parent_message_id, " +
                     "COALESCE(vc.count, 0) as view_count, " +
                     "m.like_count, m.love_count, " +
                     "COALESCE(cc.count, 0) as comment_count " +
                     "FROM message_comment m " +
                     "LEFT JOIN app_user s ON m.sender_id = s.id " +
                     "LEFT JOIN family f ON m.family_id = f.id " +
                     "LEFT JOIN (SELECT message_id, COUNT(*) as count FROM message_view GROUP BY message_id) vc " +
                     "  ON m.id = vc.message_id " +
                     "LEFT JOIN (SELECT parent_message_id, COUNT(*) as count FROM message_comment GROUP BY parent_message_id) cc " +
                     "  ON m.id = cc.parent_message_id " +
                     "WHERE m.id = ?";
        return jdbcTemplate.queryForMap(sql, commentId);
    }

    public Map<String, Object> getMessageById(Long messageId) {
        String sql = "SELECT m.id, m.content, m.sender_username, m.sender_id, m.family_id, " +
                     "m.timestamp, m.media_type, m.media_url, m.thumbnail_url, " +
                     "s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                     "f.name as family_name, " +
                     "COALESCE(vc.count, 0) as view_count, " +
                     "m.like_count, m.love_count, " +
                     "COALESCE(cc.count, 0) as comment_count " +
                     "FROM message m " +
                     "LEFT JOIN app_user s ON m.sender_id = s.id " +
                     "LEFT JOIN family f ON m.family_id = f.id " +
                     "LEFT JOIN (SELECT message_id, COUNT(*) as count FROM message_view GROUP BY message_id) vc " +
                     "  ON m.id = vc.message_id " +
                     "LEFT JOIN (SELECT parent_message_id, COUNT(*) as count FROM message_comment GROUP BY parent_message_id) cc " +
                     "  ON m.id = cc.parent_message_id " +
                     "WHERE m.id = ?";
        return jdbcTemplate.queryForMap(sql, messageId);
    }
}