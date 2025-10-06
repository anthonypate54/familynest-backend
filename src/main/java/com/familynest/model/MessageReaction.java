package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_reaction", 
       uniqueConstraints = @UniqueConstraint(name = "message_reaction_unique_reaction", 
                                            columnNames = {"user_id", "reaction_type", "target_type", "target_message_id", "target_comment_id"}))
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = true)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reaction_type", nullable = false, length = 20)
    private String reactionType;
    
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    @Column(name = "target_message_id", nullable = true)
    private Long targetMessageId;
    
    @Column(name = "target_comment_id", nullable = true)
    private Long targetCommentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getReactionType() {
        return reactionType;
    }

    public void setReactionType(String reactionType) {
        this.reactionType = reactionType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Alias method to maintain compatibility
    public void setSenderId(Long senderId) {
        this.userId = senderId;
    }
    
    // Getters and setters for new fields
    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetMessageId() {
        return targetMessageId;
    }

    public void setTargetMessageId(Long targetMessageId) {
        this.targetMessageId = targetMessageId;
    }

    public Long getTargetCommentId() {
        return targetCommentId;
    }

    public void setTargetCommentId(Long targetCommentId) {
        this.targetCommentId = targetCommentId;
    }
    
    /**
     * Helper method to set the appropriate target ID based on target type
     * @param targetId The ID of the target (message or comment)
     * @param type The target type ("MESSAGE" or "COMMENT")
     */
    public void setTargetId(Long targetId, String type) {
        this.targetType = type;
        if ("MESSAGE".equals(type)) {
            this.targetMessageId = targetId;
            this.targetCommentId = null;
            // For backward compatibility
            this.messageId = targetId;
        } else if ("COMMENT".equals(type)) {
            this.targetCommentId = targetId;
            this.targetMessageId = null;
            // For backward compatibility
            this.messageId = targetId;
        }
    }
}
