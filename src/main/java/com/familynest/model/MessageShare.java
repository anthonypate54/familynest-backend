package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_share")
public class MessageShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_message_id", nullable = false)
    private Long originalMessageId;

    @Column(name = "shared_by_user_id", nullable = false)
    private Long sharedByUserId;

    @Column(name = "shared_to_family_id", nullable = false)
    private Long sharedToFamilyId;

    @Column(name = "shared_at", nullable = false)
    private LocalDateTime sharedAt;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(Long originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    public Long getSharedByUserId() {
        return sharedByUserId;
    }

    public void setSharedByUserId(Long sharedByUserId) {
        this.sharedByUserId = sharedByUserId;
    }

    public Long getSharedToFamilyId() {
        return sharedToFamilyId;
    }

    public void setSharedToFamilyId(Long sharedToFamilyId) {
        this.sharedToFamilyId = sharedToFamilyId;
    }

    public LocalDateTime getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(LocalDateTime sharedAt) {
        this.sharedAt = sharedAt;
    }
} 
