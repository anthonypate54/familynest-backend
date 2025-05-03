package com.familynest.repository;

import com.familynest.model.UserFamilyMessageSettings;
import com.familynest.model.UserFamilyMessageSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFamilyMessageSettingsRepository extends JpaRepository<UserFamilyMessageSettings, UserFamilyMessageSettingsId> {
    
    // Find all message settings for a specific user
    List<UserFamilyMessageSettings> findByUserId(Long userId);
    
    // Find all message settings where the user has opted out (receive_messages = false)
    List<UserFamilyMessageSettings> findByUserIdAndReceiveMessagesFalse(Long userId);
    
    // Find all message settings where the user has opted in (receive_messages = true)
    List<UserFamilyMessageSettings> findByUserIdAndReceiveMessagesTrue(Long userId);
    
    // Find settings for a specific user-family combination
    Optional<UserFamilyMessageSettings> findByUserIdAndFamilyId(Long userId, Long familyId);
    
    // Find families that a user has muted
    @Query("SELECT s.familyId FROM UserFamilyMessageSettings s WHERE s.userId = :userId AND s.receiveMessages = false")
    List<Long> findMutedFamilyIdsByUserId(@Param("userId") Long userId);
    
    // Delete settings for a specific user-family combination
    void deleteByUserIdAndFamilyId(Long userId, Long familyId);
} 