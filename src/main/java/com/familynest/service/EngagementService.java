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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        reaction.setUserId(userId);
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
        comment.setUserId(userId);
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
    
    @Transactional
    public MessageComment updateComment(Long commentId, Long userId, String newContent) {
        logger.info("Updating comment: {} by user: {}", commentId, userId);
        
        Optional<MessageComment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            throw new IllegalArgumentException("Comment not found with id: " + commentId);
        }
        
        MessageComment comment = optionalComment.get();
        
        // Check if user is the author of the comment
        if (!comment.getUserId().equals(userId)) {
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
        if (!comment.getUserId().equals(userId)) {
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
        view.setUserId(userId);
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
} 