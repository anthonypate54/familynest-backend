package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_dm_notification_settings")
public class UserDMNotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "receive_dm_notifications", nullable = false)
    private Boolean receiveDMNotifications = true;

    @Column(name = "email_dm_notifications", nullable = false)
    private Boolean emailDMNotifications = true;

    @Column(name = "push_dm_notifications", nullable = false)
    private Boolean pushDMNotifications = true;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    // Constructors
    public UserDMNotificationSettings() {}

    public UserDMNotificationSettings(Long userId) {
        this.userId = userId;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Boolean getReceiveDMNotifications() {
        return receiveDMNotifications;
    }

    public void setReceiveDMNotifications(Boolean receiveDMNotifications) {
        this.receiveDMNotifications = receiveDMNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getEmailDMNotifications() {
        return emailDMNotifications;
    }

    public void setEmailDMNotifications(Boolean emailDMNotifications) {
        this.emailDMNotifications = emailDMNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getPushDMNotifications() {
        return pushDMNotifications;
    }

    public void setPushDMNotifications(Boolean pushDMNotifications) {
        this.pushDMNotifications = pushDMNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 
