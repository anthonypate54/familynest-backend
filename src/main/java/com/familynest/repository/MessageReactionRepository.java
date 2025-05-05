package com.familynest.repository;

import com.familynest.model.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    // Find all reactions for a message
    List<MessageReaction> findByMessageId(Long messageId);
    
    // Find all reactions by a user
    List<MessageReaction> findByUserId(Long userId);
    
    // Find all reactions of a specific type for a message
    List<MessageReaction> findByMessageIdAndReactionType(Long messageId, String reactionType);
    
    // Find a specific reaction by a user on a message
    Optional<MessageReaction> findByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);
    
    // Delete a specific reaction by a user on a message
    void deleteByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);
    
    // Count reactions by type for a message
    long countByMessageIdAndReactionType(Long messageId, String reactionType);
    
    // Count total reactions for a message
    long countByMessageId(Long messageId);
} 