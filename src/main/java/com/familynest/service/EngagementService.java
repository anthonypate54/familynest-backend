package com.familynest.service;

import com.familynest.model.*;
import com.familynest.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
public class EngagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(EngagementService.class);
    
    @Autowired
    private MessageReactionRepository reactionRepository;
    
    @Autowired
    private MessageCommentRepository commentRepository;
    
    @Autowired
    private MessageViewRepository viewRepository;
    
    @Autowired
    private MessageShareRepository shareRepository;
    
    @Autowired
    private UserEngagementSettingsRepository settingsRepository;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // ----- Reaction methods -----
    
    @Transactional
    public MessageReaction addReaction(Long messageId, Long userId, String reactionType) {
        logger.info("Adding reaction: {} to message: {} by user: {}", reactionType, messageId, userId);
        
        // Check if message exists
        if (!messageRepository.existsById(messageId)) {
            throw new IllegalArgumentException("Message not found with id: " + messageId);
        }
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        
        // Check if reaction already exists, remove it if so (toggle behavior)
        Optional<MessageReaction> existingReaction = 
            reactionRepository.findByMessageIdAndUserIdAndReactionType(messageId, userId, reactionType);
        
        if (existingReaction.isPresent()) {
            reactionRepository.delete(existingReaction.get());
            logger.info("Removed existing reaction: {}", existingReaction.get().getId());
            return null;
        }
        
        // Create and save new reaction
        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(messageId);
        reaction.setSenderId(userId);
        reaction.setReactionType(reactionType);
        reaction.setCreatedAt(LocalDateTime.now());
        
        return reactionRepository.save(reaction);
    }
    
    public List<MessageReaction> getMessageReactions(Long messageId) {
        logger.info("Getting reactions for message: {}", messageId);
        return reactionRepository.findByMessageId(messageId);
    }
    
    public Map<String, Long> getMessageReactionCounts(Long messageId) {
        logger.info("Getting reaction counts for message: {}", messageId);
        List<MessageReaction> reactions = reactionRepository.findByMessageId(messageId);
        
        // Group by reaction type and count
        Map<String, Long> counts = new HashMap<>();
        for (MessageReaction reaction : reactions) {
            String type = reaction.getReactionType();
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        }
        
        return counts;
    }
    
    @Transactional
    public void removeReaction(Long messageId, Long userId, String reactionType) {
        logger.info("Removing reaction: {} from message: {} by user: {}", reactionType, messageId, userId);
        reactionRepository.deleteByMessageIdAndUserIdAndReactionType(messageId, userId, reactionType);
    }
    
    // ----- Comment methods -----
    
    @Transactional
    public MessageComment addComment(Long messageId, Long userId, String content, Long parentCommentId) {
        logger.info("Adding comment to message: {} by user: {}", messageId, userId);
        
        // Check if message exists
        if (!messageRepository.existsById(messageId)) {
            throw new IllegalArgumentException("Message not found with id: " + messageId);
        }
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        
        // If parent comment provided, check if it exists
        if (parentCommentId != null && !commentRepository.existsById(parentCommentId)) {
            throw new IllegalArgumentException("Parent comment not found with id: " + parentCommentId);
        }
        
        // Create and save new comment
        MessageComment comment = new MessageComment();
        comment.setMessageId(messageId);
        comment.setSenderId(userId);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setParentCommentId(parentCommentId);
        
        return commentRepository.save(comment);
    }
    
    public Page<MessageComment> getMessageComments(Long messageId, Pageable pageable) {
        logger.info("Getting comments for message: {} with pagination", messageId);
        return commentRepository.findByMessageIdAndParentCommentIdIsNull(messageId, pageable);
    }
    
    public List<MessageComment> getCommentReplies(Long commentId) {
        logger.info("Getting replies for comment: {}", commentId);
        return commentRepository.findByParentCommentId(commentId);
    }
    
    public long getCommentReplyCount(Long commentId) {
        logger.info("Getting reply count for comment: {}", commentId);
        return commentRepository.countByParentCommentId(commentId);
    }
    
    @Transactional
    public MessageComment updateComment(Long commentId, Long userId, String newContent) {
        logger.info("Updating comment: {} by user: {}", commentId, userId);
        
        Optional<MessageComment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            throw new IllegalArgumentException("Comment not found with id: " + commentId);
        }
        
        MessageComment comment = optionalComment.get();
        
        // Check if user is the author of the comment
        if (!comment.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("User is not authorized to update this comment");
        }
        
        comment.setContent(newContent);
        comment.setUpdatedAt(LocalDateTime.now());
        
        return commentRepository.save(comment);
    }
    
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        logger.info("Deleting comment: {} by user: {}", commentId, userId);
        
        Optional<MessageComment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            throw new IllegalArgumentException("Comment not found with id: " + commentId);
        }
        
        MessageComment comment = optionalComment.get();
        
        // Check if user is the author of the comment
        if (!comment.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("User is not authorized to delete this comment");
        }
        
        // Delete the comment and all its replies
        commentRepository.deleteById(commentId);
    }
    
    // ----- View methods -----
    
    @Transactional
    public MessageView markMessageAsViewed(Long messageId, Long userId) {
        logger.info("Marking message: {} as viewed by user: {}", messageId, userId);
        
        // Check if message exists
        if (!messageRepository.existsById(messageId)) {
            throw new IllegalArgumentException("Message not found with id: " + messageId);
        }
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        
        // Check if already viewed
        Optional<MessageView> existingView = viewRepository.findByMessageIdAndUserId(messageId, userId);
        if (existingView.isPresent()) {
            logger.info("Message already viewed by user");
            return existingView.get();
        }
        
        // Create and save new view
        MessageView view = new MessageView();
        view.setMessageId(messageId);
        view.setSenderId(userId);
        view.setViewedAt(LocalDateTime.now());
        
        return viewRepository.save(view);
    }
    
    public List<MessageView> getMessageViews(Long messageId) {
        logger.info("Getting views for message: {}", messageId);
        return viewRepository.findByMessageId(messageId);
    }
    
    public long getMessageViewCount(Long messageId) {
        logger.info("Getting view count for message: {}", messageId);
        return viewRepository.countByMessageId(messageId);
    }
    
    public boolean isMessageViewedByUser(Long messageId, Long userId) {
        logger.info("Checking if message: {} is viewed by user: {}", messageId, userId);
        return viewRepository.findByMessageIdAndUserId(messageId, userId).isPresent();
    }
    
    // ----- Share methods -----
    
    @Transactional
    public MessageShare shareMessage(Long messageId, Long userId, Long targetFamilyId) {
        logger.info("Sharing message: {} by user: {} to family: {}", messageId, userId, targetFamilyId);
        
        // Check if message exists
        if (!messageRepository.existsById(messageId)) {
            throw new IllegalArgumentException("Message not found with id: " + messageId);
        }
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        
        // Check if already shared to this family by this user
        if (shareRepository.existsByOriginalMessageIdAndSharedByUserIdAndSharedToFamilyId(
                messageId, userId, targetFamilyId)) {
            throw new IllegalArgumentException("Message already shared to this family by this user");
        }
        
        // Create and save new share
        MessageShare share = new MessageShare();
        share.setOriginalMessageId(messageId);
        share.setSharedByUserId(userId);
        share.setSharedToFamilyId(targetFamilyId);
        share.setSharedAt(LocalDateTime.now());
        
        return shareRepository.save(share);
    }
    
    public List<MessageShare> getMessageShares(Long messageId) {
        logger.info("Getting shares for message: {}", messageId);
        return shareRepository.findByOriginalMessageId(messageId);
    }
    
    public long getMessageShareCount(Long messageId) {
        logger.info("Getting share count for message: {}", messageId);
        return shareRepository.countByOriginalMessageId(messageId);
    }
    
    // ----- Engagement Settings methods -----
    
    public UserEngagementSettings getUserEngagementSettings(Long userId) {
        logger.info("Getting engagement settings for user: {}", userId);
        return settingsRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User settings not found for user: " + userId));
    }
    
    @Transactional
    public UserEngagementSettings updateUserEngagementSettings(Long userId, UserEngagementSettings settings) {
        logger.info("Updating engagement settings for user: {}", userId);
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        
        // Ensure the settings are for the correct user
        settings.setUserId(userId);
        
        return settingsRepository.save(settings);
    }
    
    // ----- Combined Engagement Data methods -----
    
    public Map<String, Object> getMessageEngagementData(Long messageId) {
        logger.info("Getting combined engagement data for message: {}", messageId);
        
        Map<String, Object> data = new HashMap<>();
        
        // Add reaction counts
        data.put("reactions", getMessageReactionCounts(messageId));
        
        // Add total comment count
        data.put("commentCount", commentRepository.countByMessageId(messageId));
        
        // Add view count
        data.put("viewCount", viewRepository.countByMessageId(messageId));
        
        // Add share count
        data.put("shareCount", shareRepository.countByOriginalMessageId(messageId));
        
        return data;
    }
    
    /**
     * Get engagement data for multiple messages in one efficient database query
     * This is much more optimized than calling getMessageEngagementData() multiple times
     */
    @Cacheable(value = "messageEngagement", key = "#messageIds.hashCode()")
    public Map<Long, Map<String, Object>> getBatchMessageEngagementData(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        logger.info("Getting batch engagement data for {} messages", messageIds.size());
        
        // Limit to a reasonable batch size
        List<Long> limitedIds = messageIds.size() <= 50 ? messageIds : messageIds.subList(0, 50);
        
        // Use the array constructor for better performance with arrays
        StringBuilder idArray = new StringBuilder("ARRAY[");
        for (int i = 0; i < limitedIds.size(); i++) {
            if (i > 0) idArray.append(",");
            idArray.append(limitedIds.get(i));
        }
        idArray.append("]");
        
        // Create optimized query using more efficient array handling
        String sql = "WITH message_ids AS (" +
                   "  SELECT UNNEST(" + idArray.toString() + ") AS id" +
                   "), " +
                   "engagement_data AS (" +
                   "  -- Views" +
                   "  SELECT 'view' AS type, message_id, COUNT(*) AS count " +
                   "  FROM message_view " +
                   "  WHERE message_id IN (SELECT id FROM message_ids) " +
                   "  GROUP BY message_id " +
                   "  " +
                   "  UNION ALL " +
                   "  " +
                   "  -- Reactions " +
                   "  SELECT 'reaction' AS type, message_id, COUNT(*) AS count " +
                   "  FROM message_reaction " +
                   "  WHERE message_id IN (SELECT id FROM message_ids) " +
                   "  GROUP BY message_id " +
                   "  " +
                   "  UNION ALL " +
                   "  " +
                   "  -- Comments " +
                   "  SELECT 'comment' AS type, message_id, COUNT(*) AS count " +
                   "  FROM message_comment " +
                   "  WHERE message_id IN (SELECT id FROM message_ids) " +
                   "  GROUP BY message_id " +
                   "  " +
                   "  UNION ALL " +
                   "  " +
                   "  -- Shares " +
                   "  SELECT 'share' AS type, original_message_id AS message_id, COUNT(*) AS count " +
                   "  FROM message_share " +
                   "  WHERE original_message_id IN (SELECT id FROM message_ids) " +
                   "  GROUP BY original_message_id " +
                   "), " +
                   "engagement_summary AS (" +
                   "  SELECT " +
                   "    message_id, " +
                   "    SUM(CASE WHEN type = 'view' THEN count ELSE 0 END) AS view_count, " +
                   "    SUM(CASE WHEN type = 'reaction' THEN count ELSE 0 END) AS reaction_count, " +
                   "    SUM(CASE WHEN type = 'comment' THEN count ELSE 0 END) AS comment_count, " +
                   "    SUM(CASE WHEN type = 'share' THEN count ELSE 0 END) AS share_count " +
                   "  FROM engagement_data " +
                   "  GROUP BY message_id" +
                   "), " +
                   "reactions_detail AS (" +
                   "  SELECT message_id, reaction_type, COUNT(*) AS count " +
                   "  FROM message_reaction " +
                   "  WHERE message_id IN (SELECT id FROM message_ids) " +
                   "  GROUP BY message_id, reaction_type" +
                   ")" +
                   "SELECT " +
                   "  mi.id AS message_id, " +
                   "  COALESCE(es.view_count, 0) AS view_count, " +
                   "  COALESCE(es.comment_count, 0) AS comment_count, " +
                   "  COALESCE(es.share_count, 0) AS share_count, " +
                   "  COALESCE(es.reaction_count, 0) AS total_reaction_count, " +
                   "  rd.reaction_type, " +
                   "  COALESCE(rd.count, 0) AS reaction_type_count " +
                   "FROM message_ids mi " +
                   "LEFT JOIN engagement_summary es ON mi.id = es.message_id " +
                   "LEFT JOIN reactions_detail rd ON mi.id = rd.message_id";
        
        // Execute query directly with no parameters needed (ids are in the query itself)
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        // Process results
        Map<Long, Map<String, Object>> messageEngagementMap = new HashMap<>();
        Map<Long, Map<String, Integer>> reactionsMap = new HashMap<>();
        
        for (Map<String, Object> row : results) {
            Long messageId = ((Number) row.get("message_id")).longValue();
            
            // Initialize data structures for this message if not exist
            if (!messageEngagementMap.containsKey(messageId)) {
                Map<String, Object> messageData = new HashMap<>();
                messageData.put("viewCount", row.get("view_count"));
                messageData.put("commentCount", row.get("comment_count"));
                messageData.put("shareCount", row.get("share_count"));
                messageData.put("reactions", new HashMap<String, Integer>());
                messageEngagementMap.put(messageId, messageData);
                reactionsMap.put(messageId, new HashMap<>());
            }
            
            // Add reaction data if present
            if (row.get("reaction_type") != null) {
                String reactionType = (String) row.get("reaction_type");
                Integer count = ((Number) row.get("reaction_type_count")).intValue();
                reactionsMap.get(messageId).put(reactionType, count);
            }
        }
        
        // Add reactions to each message
        for (Long messageId : messageEngagementMap.keySet()) {
            messageEngagementMap.get(messageId).put("reactions", reactionsMap.get(messageId));
        }
        
        return messageEngagementMap;
    }
} 