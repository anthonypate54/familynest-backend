package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "user_engagement_settings")
public class UserEngagementSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "show_reactions_to_others", nullable = false)
    private Boolean showReactionsToOthers = true;

    @Column(name = "show_my_views_to_others", nullable = false)
    private Boolean showMyViewsToOthers = true;

    @Column(name = "allow_sharing_my_messages", nullable = false)
    private Boolean allowSharingMyMessages = true;

    @Column(name = "notify_on_reactions", nullable = false)
    private Boolean notifyOnReactions = true;

    @Column(name = "notify_on_comments", nullable = false)
    private Boolean notifyOnComments = true;

    @Column(name = "notify_on_shares", nullable = false)
    private Boolean notifyOnShares = true;

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

    public Boolean getShowReactionsToOthers() {
        return showReactionsToOthers;
    }

    public void setShowReactionsToOthers(Boolean showReactionsToOthers) {
        this.showReactionsToOthers = showReactionsToOthers;
    }

    public Boolean getShowMyViewsToOthers() {
        return showMyViewsToOthers;
    }

    public void setShowMyViewsToOthers(Boolean showMyViewsToOthers) {
        this.showMyViewsToOthers = showMyViewsToOthers;
    }

    public Boolean getAllowSharingMyMessages() {
        return allowSharingMyMessages;
    }

    public void setAllowSharingMyMessages(Boolean allowSharingMyMessages) {
        this.allowSharingMyMessages = allowSharingMyMessages;
    }

    public Boolean getNotifyOnReactions() {
        return notifyOnReactions;
    }

    public void setNotifyOnReactions(Boolean notifyOnReactions) {
        this.notifyOnReactions = notifyOnReactions;
    }

    public Boolean getNotifyOnComments() {
        return notifyOnComments;
    }

    public void setNotifyOnComments(Boolean notifyOnComments) {
        this.notifyOnComments = notifyOnComments;
    }

    public Boolean getNotifyOnShares() {
        return notifyOnShares;
    }

    public void setNotifyOnShares(Boolean notifyOnShares) {
        this.notifyOnShares = notifyOnShares;
    }
} 
