package com.familynest.repository;

import com.familynest.model.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    // Legacy methods using message_id (kept for backward compatibility)
    List<MessageReaction> findByMessageId(Long messageId);
    List<MessageReaction> findByMessageIdAndReactionType(Long messageId, String reactionType);
    Optional<MessageReaction> findByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);
    void deleteByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);
    long countByMessageIdAndReactionType(Long messageId, String reactionType);
    long countByMessageId(Long messageId);
    
    // Find all reactions by a user
    List<MessageReaction> findByUserId(Long userId);
    
    // New methods using target_message_id for message reactions
    List<MessageReaction> findByTargetMessageId(Long targetMessageId);
    List<MessageReaction> findByTargetMessageIdAndReactionType(Long targetMessageId, String reactionType);
    Optional<MessageReaction> findByTargetMessageIdAndUserIdAndReactionTypeAndTargetType(
        Long targetMessageId, Long userId, String reactionType, String targetType);
    void deleteByTargetMessageIdAndUserIdAndReactionTypeAndTargetType(
        Long targetMessageId, Long userId, String reactionType, String targetType);
    long countByTargetMessageIdAndReactionType(Long targetMessageId, String reactionType);
    long countByTargetMessageId(Long targetMessageId);
    
    // New methods using target_comment_id for comment reactions
    List<MessageReaction> findByTargetCommentId(Long targetCommentId);
    List<MessageReaction> findByTargetCommentIdAndReactionType(Long targetCommentId, String reactionType);
    Optional<MessageReaction> findByTargetCommentIdAndUserIdAndReactionTypeAndTargetType(
        Long targetCommentId, Long userId, String reactionType, String targetType);
    void deleteByTargetCommentIdAndUserIdAndReactionTypeAndTargetType(
        Long targetCommentId, Long userId, String reactionType, String targetType);
    long countByTargetCommentIdAndReactionType(Long targetCommentId, String reactionType);
    long countByTargetCommentId(Long targetCommentId);
}
