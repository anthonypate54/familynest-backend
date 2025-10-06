package com.familynest.model;

/**
 * A wrapper class for message preferences to be used in tests and API responses.
 * This class serves as a DTO to standardize preference formats.
 */
public class MessagePreference {
    private Long userId;
    private Long familyId;
    private Long memberUserId; // Optional, only for member-level preferences
    private Boolean receiveMessages;
    
    // Default constructor
    public MessagePreference() {
    }
    
    // Constructor for family-level preferences
    public MessagePreference(Long userId, Long familyId, Boolean receiveMessages) {
        this.userId = userId;
        this.familyId = familyId;
        this.receiveMessages = receiveMessages;
    }
    
    // Constructor for member-level preferences
    public MessagePreference(Long userId, Long familyId, Long memberUserId, Boolean receiveMessages) {
        this.userId = userId;
        this.familyId = familyId;
        this.memberUserId = memberUserId;
        this.receiveMessages = receiveMessages;
    }
    
    // Factory method to create from UserFamilyMessageSettings
    public static MessagePreference fromFamilySettings(UserFamilyMessageSettings settings) {
        return new MessagePreference(
            settings.getUserId(),
            settings.getFamilyId(),
            settings.getReceiveMessages()
        );
    }
    
    // Factory method to create from UserMemberMessageSettings
    public static MessagePreference fromMemberSettings(UserMemberMessageSettings settings) {
        return new MessagePreference(
            settings.getUserId(),
            settings.getFamilyId(),
            settings.getMemberUserId(),
            settings.getReceiveMessages()
        );
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
    
    public boolean isMemberPreference() {
        return memberUserId != null;
    }
    
    @Override
    public String toString() {
        if (isMemberPreference()) {
            return "MessagePreference{" +
                   "userId=" + userId +
                   ", familyId=" + familyId +
                   ", memberUserId=" + memberUserId +
                   ", receiveMessages=" + receiveMessages +
                   '}';
        } else {
            return "MessagePreference{" +
                   "userId=" + userId +
                   ", familyId=" + familyId +
                   ", receiveMessages=" + receiveMessages +
                   '}';
        }
    }
} 
