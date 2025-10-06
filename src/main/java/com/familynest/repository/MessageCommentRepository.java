package com.familynest.repository;

import com.familynest.model.MessageComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageCommentRepository extends JpaRepository<MessageComment, Long> {
    // Find all top-level comments for a message (no parent comment)
    List<MessageComment> findByParentMessageIdAndParentCommentIdIsNull(Long parentMessageId);
    
    // Find all top-level comments for a message with pagination
    Page<MessageComment> findByParentMessageIdAndParentCommentIdIsNull(Long parentMessageId, Pageable pageable);
    
    // Alias method for compatibility
    default Page<MessageComment> findByMessageIdAndParentCommentIdIsNull(Long messageId, Pageable pageable) {
        return findByParentMessageIdAndParentCommentIdIsNull(messageId, pageable);
    }
    
    // Find all replies to a specific comment
    List<MessageComment> findByParentCommentId(Long parentCommentId);
    
    // Find all comments by a specific user
    List<MessageComment> findBySenderId(Long senderId);
    
    // Count comments for a message
    long countByParentMessageId(Long parentMessageId);
    
    // Alias method for compatibility
    default long countByMessageId(Long messageId) {
        return countByParentMessageId(messageId);
    }
    
    // Count replies for a comment
    long countByParentCommentId(Long parentCommentId);
    
    // Custom query to get comments with reply counts
    @Query("SELECT c, COUNT(r) FROM MessageComment c LEFT JOIN MessageComment r ON r.parentCommentId = c.id " +
           "WHERE c.parentMessageId = :parentMessageId AND c.parentCommentId IS NULL " +
           "GROUP BY c.id ORDER BY c.createdAt DESC")
    List<Object[]> findCommentsWithReplyCount(@Param("parentMessageId") Long parentMessageId);
} 
