package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_member_message_settings", schema = "public")
@IdClass(UserMemberMessageSettingsId.class)
public class UserMemberMessageSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "family_id")
    private Long familyId;
    
    @Id
    @Column(name = "member_user_id")
    private Long memberUserId;

    @Column(name = "receive_messages")
    private Boolean receiveMessages;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public UserMemberMessageSettings() {
    }

    public UserMemberMessageSettings(Long userId, Long familyId, Long memberUserId, Boolean receiveMessages) {
        this.userId = userId;
        this.familyId = familyId;
        this.memberUserId = memberUserId;
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

    public Long getMemberUserId() {
        return memberUserId;
    }

    public void setMemberUserId(Long memberUserId) {
        this.memberUserId = memberUserId;
    }

    public Boolean getReceiveMessages() {
        return receiveMessages;
    }

    public void setReceiveMessages(Boolean receiveMessages) {
        this.receiveMessages = receiveMessages;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 