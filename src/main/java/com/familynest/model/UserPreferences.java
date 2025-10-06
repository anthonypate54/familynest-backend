package com.familynest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    // Demographics/Privacy Settings
    @Column(name = "show_address")
    private Boolean showAddress = true;

    @Column(name = "show_phone_number")
    private Boolean showPhoneNumber = true;

    @Column(name = "show_birthday")
    private Boolean showBirthday = true;

    // Notification Preferences
    @Column(name = "family_messages_notifications")
    private Boolean familyMessagesNotifications = true;

    @Column(name = "new_member_notifications")
    private Boolean newMemberNotifications = true;

    @Column(name = "invitation_notifications")
    private Boolean invitationNotifications = true;

    // Metadata
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserPreferences() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserPreferences(Long userId) {
        this();
        this.userId = userId;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getShowAddress() {
        return showAddress;
    }

    public void setShowAddress(Boolean showAddress) {
        this.showAddress = showAddress;
    }

    public Boolean getShowPhoneNumber() {
        return showPhoneNumber;
    }

    public void setShowPhoneNumber(Boolean showPhoneNumber) {
        this.showPhoneNumber = showPhoneNumber;
    }

    public Boolean getShowBirthday() {
        return showBirthday;
    }

    public void setShowBirthday(Boolean showBirthday) {
        this.showBirthday = showBirthday;
    }

    public Boolean getFamilyMessagesNotifications() {
        return familyMessagesNotifications;
    }

    public void setFamilyMessagesNotifications(Boolean familyMessagesNotifications) {
        this.familyMessagesNotifications = familyMessagesNotifications;
    }

    public Boolean getNewMemberNotifications() {
        return newMemberNotifications;
    }

    public void setNewMemberNotifications(Boolean newMemberNotifications) {
        this.newMemberNotifications = newMemberNotifications;
    }

    public Boolean getInvitationNotifications() {
        return invitationNotifications;
    }

    public void setInvitationNotifications(Boolean invitationNotifications) {
        this.invitationNotifications = invitationNotifications;
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
}
