package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Temporarily commented out - table will be dropped in V32
// @Entity
// @Table(name = "user_invitation_notification_settings")
public class UserInvitationNotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "receive_invitation_notifications", nullable = false)
    private Boolean receiveInvitationNotifications = true;

    @Column(name = "email_invitation_notifications", nullable = false)
    private Boolean emailInvitationNotifications = true;

    @Column(name = "push_invitation_notifications", nullable = false)
    private Boolean pushInvitationNotifications = true;

    @Column(name = "notify_on_invitation_accepted", nullable = false)
    private Boolean notifyOnInvitationAccepted = true;

    @Column(name = "notify_on_invitation_declined", nullable = false)
    private Boolean notifyOnInvitationDeclined = false;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    // Constructors
    public UserInvitationNotificationSettings() {}

    public UserInvitationNotificationSettings(Long userId) {
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

    public Boolean getReceiveInvitationNotifications() {
        return receiveInvitationNotifications;
    }

    public void setReceiveInvitationNotifications(Boolean receiveInvitationNotifications) {
        this.receiveInvitationNotifications = receiveInvitationNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getEmailInvitationNotifications() {
        return emailInvitationNotifications;
    }

    public void setEmailInvitationNotifications(Boolean emailInvitationNotifications) {
        this.emailInvitationNotifications = emailInvitationNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getPushInvitationNotifications() {
        return pushInvitationNotifications;
    }

    public void setPushInvitationNotifications(Boolean pushInvitationNotifications) {
        this.pushInvitationNotifications = pushInvitationNotifications;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getNotifyOnInvitationAccepted() {
        return notifyOnInvitationAccepted;
    }

    public void setNotifyOnInvitationAccepted(Boolean notifyOnInvitationAccepted) {
        this.notifyOnInvitationAccepted = notifyOnInvitationAccepted;
        this.lastUpdated = LocalDateTime.now();
    }

    public Boolean getNotifyOnInvitationDeclined() {
        return notifyOnInvitationDeclined;
    }

    public void setNotifyOnInvitationDeclined(Boolean notifyOnInvitationDeclined) {
        this.notifyOnInvitationDeclined = notifyOnInvitationDeclined;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
} 