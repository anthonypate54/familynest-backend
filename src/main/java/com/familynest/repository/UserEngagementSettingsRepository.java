package com.familynest.repository;

import com.familynest.model.UserEngagementSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserEngagementSettingsRepository extends JpaRepository<UserEngagementSettings, Long> {
    // The ID of this entity is the user ID, so findById will be used to get settings for a user
    
    // Find users who want to be notified about reactions
    List<UserEngagementSettings> findByNotifyOnReactionsTrue();
    
    // Find users who want to be notified about comments
    List<UserEngagementSettings> findByNotifyOnCommentsTrue();
    
    // Find users who want to be notified about shares
    List<UserEngagementSettings> findByNotifyOnSharesTrue();
    
    // Find users who allow their messages to be shared
    List<UserEngagementSettings> findByAllowSharingMyMessagesTrue();
    
    // Find users who show their reactions to others
    List<UserEngagementSettings> findByShowReactionsToOthersTrue();
    
    // Find users who show their message views
    List<UserEngagementSettings> findByShowMyViewsToOthersTrue();
} 