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
    List<MessageComment> findByMessageIdAndParentCommentIdIsNull(Long messageId);
    
    // Find all top-level comments for a message with pagination
    Page<MessageComment> findByMessageIdAndParentCommentIdIsNull(Long messageId, Pageable pageable);
    
    // Find all replies to a specific comment
    List<MessageComment> findByParentCommentId(Long parentCommentId);
    
    // Find all comments by a specific user
    List<MessageComment> findByUserId(Long userId);
    
    // Count comments for a message
    long countByMessageId(Long messageId);
    
    // Count replies for a comment
    long countByParentCommentId(Long parentCommentId);
    
    // Custom query to get comments with reply counts
    @Query("SELECT c, COUNT(r) FROM MessageComment c LEFT JOIN MessageComment r ON r.parentCommentId = c.id " +
           "WHERE c.messageId = :messageId AND c.parentCommentId IS NULL " +
           "GROUP BY c.id ORDER BY c.createdAt DESC")
    List<Object[]> findCommentsWithReplyCount(@Param("messageId") Long messageId);
} 