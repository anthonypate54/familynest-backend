package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_view")
public class MessageView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = true)
    private Long messageId;

    @Column(name = "dm_message_id", nullable = true)
    private Long dmMessageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @Column(name = "message_type", nullable = false)
    private String messageType = "family";

    // Constructors
    public MessageView() {}

    // Factory methods for different message types
    public static MessageView forFamilyMessage(Long messageId, Long userId) {
        MessageView view = new MessageView();
        view.messageId = messageId;
        view.userId = userId;
        view.messageType = "family";
        view.viewedAt = LocalDateTime.now();
        return view;
    }

    public static MessageView forDMMessage(Long dmMessageId, Long userId) {
        MessageView view = new MessageView();
        view.dmMessageId = dmMessageId;
        view.userId = userId;
        view.messageType = "dm";
        view.viewedAt = LocalDateTime.now();
        return view;
    }

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

    public Long getDmMessageId() {
        return dmMessageId;
    }

    public void setDmMessageId(Long dmMessageId) {
        this.dmMessageId = dmMessageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(LocalDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    // Utility methods
    public boolean isFamilyMessage() {
        return "family".equals(messageType);
    }

    public boolean isDMMessage() {
        return "dm".equals(messageType);
    }

    public Long getActualMessageId() {
        return isFamilyMessage() ? messageId : dmMessageId;
    }

    // Alias method to maintain compatibility
    public void setSenderId(Long senderId) {
        this.userId = senderId;
    }
} 