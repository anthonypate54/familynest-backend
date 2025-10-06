package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification_settings")
public class UserNotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_permission_granted", nullable = false)
    private Boolean devicePermissionGranted = false;

    @Column(name = "push_notifications_enabled", nullable = false)
    private Boolean pushNotificationsEnabled = false;

    @Column(name = "email_notifications_enabled", nullable = false) 
    private Boolean emailNotificationsEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public UserNotificationSettings() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserNotificationSettings(Long userId) {
        this.userId = userId;
        this.devicePermissionGranted = false;
        this.pushNotificationsEnabled = false;
        this.emailNotificationsEnabled = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserNotificationSettings(Long userId, Boolean devicePermission, Boolean pushEnabled, Boolean emailEnabled) {
        this.userId = userId;
        this.devicePermissionGranted = devicePermission;
        this.pushNotificationsEnabled = pushEnabled;
        this.emailNotificationsEnabled = emailEnabled;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getDevicePermissionGranted() {
        return devicePermissionGranted;
    }

    public void setDevicePermissionGranted(Boolean devicePermissionGranted) {
        this.devicePermissionGranted = devicePermissionGranted;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getPushNotificationsEnabled() {
        return pushNotificationsEnabled;
    }

    public void setPushNotificationsEnabled(Boolean pushNotificationsEnabled) {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "UserNotificationSettings{" +
                "userId=" + userId +
                ", devicePermissionGranted=" + devicePermissionGranted +
                ", pushNotificationsEnabled=" + pushNotificationsEnabled +
                ", emailNotificationsEnabled=" + emailNotificationsEnabled +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
} 
