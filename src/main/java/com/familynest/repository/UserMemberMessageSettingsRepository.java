package com.familynest.repository;

import com.familynest.model.UserMemberMessageSettings;
import com.familynest.model.UserMemberMessageSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMemberMessageSettingsRepository extends JpaRepository<UserMemberMessageSettings, UserMemberMessageSettingsId> {
    
    // Find all settings for a specific user
    List<UserMemberMessageSettings> findByUserId(Long userId);
    
    // Find all settings for a specific user in a specific family
    List<UserMemberMessageSettings> findByUserIdAndFamilyId(Long userId, Long familyId);
    
    // Find a specific setting for a user, family, and member
    Optional<UserMemberMessageSettings> findByUserIdAndFamilyIdAndMemberUserId(Long userId, Long familyId, Long memberUserId);
    
    // Find all muted members in a specific family for a user
    @Query("SELECT s.memberUserId FROM UserMemberMessageSettings s WHERE s.userId = :userId AND s.familyId = :familyId AND s.receiveMessages = false")
    List<Long> findMutedMemberIdsByUserIdAndFamilyId(@Param("userId") Long userId, @Param("familyId") Long familyId);
    
    // Delete a specific setting
    void deleteByUserIdAndFamilyIdAndMemberUserId(Long userId, Long familyId, Long memberUserId);
} 
