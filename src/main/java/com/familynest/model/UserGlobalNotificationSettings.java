package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Temporarily commented out - table will be dropped in V32
// @Entity
// @Table(name = "user_global_notification_settings")
public class UserGlobalNotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = true;

    @Column(name = "push_notifications_enabled", nullable = false)
    private Boolean pushNotificationsEnabled = true;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", nullable = false)
    private LocalTime quietHoursStart = LocalTime.of(22, 0);

    @Column(name = "quiet_hours_end", nullable = false)
    private LocalTime quietHoursEnd = LocalTime.of(8, 0);

    @Column(name = "weekend_notifications", nullable = false)
    private Boolean weekendNotifications = true;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    // Constructors
    public UserGlobalNotificationSettings() {}

    public UserGlobalNotificationSettings(Long userId) {
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

    public Boolean getEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getPushNotificationsEnabled() {
        return pushNotificationsEnabled;
    }

    public void setPushNotificationsEnabled(Boolean pushNotificationsEnabled) {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getQuietHoursEnabled() {
        return quietHoursEnabled;
    }

    public void setQuietHoursEnabled(Boolean quietHoursEnabled) {
        this.quietHoursEnabled = quietHoursEnabled;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(LocalTime quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(LocalTime quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getWeekendNotifications() {
        return weekendNotifications;
    }

    public void setWeekendNotifications(Boolean weekendNotifications) {
        this.weekendNotifications = weekendNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 