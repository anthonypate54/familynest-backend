package com.familynest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Settings for user message preferences for specific families.
 * Uses a composite key of user_id and family_id.
 */
@Entity
@Table(name = "user_family_message_settings")
@IdClass(UserFamilyMessageSettingsId.class)
public class UserFamilyMessageSettings {
    
    @Id
    @Column(name = "user_id")
    private Long userId;
    
    @Id
    @Column(name = "family_id")
    private Long familyId;
    
    @Column(name = "receive_messages", nullable = false)
    private Boolean receiveMessages = true;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    // Default constructor
    public UserFamilyMessageSettings() {
    }
    
    // Constructor with required fields
    public UserFamilyMessageSettings(Long userId, Long familyId, Boolean receiveMessages) {
        this.userId = userId;
        this.familyId = familyId;
        this.receiveMessages = receiveMessages;
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Getters and setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getFamilyId() {
        return familyId;
    }
    
    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }
    
    public Boolean getReceiveMessages() {
        return receiveMessages;
    }
    
    public void setReceiveMessages(Boolean receiveMessages) {
        this.receiveMessages = receiveMessages;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 