package com.familynest.repository;

import com.familynest.model.MessageShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageShareRepository extends JpaRepository<MessageShare, Long> {
    // Find all shares of a message
    List<MessageShare> findByOriginalMessageId(Long originalMessageId);
    
    // Find all shares by a user
    List<MessageShare> findBySharedByUserId(Long sharedByUserId);
    
    // Find all shares to a family
    List<MessageShare> findBySharedToFamilyId(Long sharedToFamilyId);
    
    // Count how many times a message was shared
    long countByOriginalMessageId(Long originalMessageId);
    
    // Find if a message was already shared to a family by a user
    boolean existsByOriginalMessageIdAndSharedByUserIdAndSharedToFamilyId(
        Long originalMessageId, Long sharedByUserId, Long sharedToFamilyId);
    
    // Find all messages shared to families where user is a member
    @Query("SELECT ms FROM MessageShare ms " +
           "JOIN UserFamilyMembership ufm ON ms.sharedToFamilyId = ufm.familyId " +
           "WHERE ufm.userId = :userId")
    List<MessageShare> findSharedMessagesVisibleToUser(@Param("userId") Long userId);
} 
