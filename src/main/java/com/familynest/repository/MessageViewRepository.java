package com.familynest.repository;

import com.familynest.model.MessageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageViewRepository extends JpaRepository<MessageView, Long> {
    // Find view record by message and user
    Optional<MessageView> findByMessageIdAndUserId(Long messageId, Long userId);
    
    // Find all views for a message
    List<MessageView> findByMessageId(Long messageId);
    
    // Count views for a message
    long countByMessageId(Long messageId);
    
    // Find all views by a user
    List<MessageView> findByUserId(Long userId);
    
    // Find messages viewed by a user in a family
    @Query("SELECT v FROM MessageView v JOIN Message m ON v.messageId = m.id " +
           "WHERE v.userId = :userId AND m.familyId = :familyId")
    List<MessageView> findViewsByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);
    
    // Check if a message has been viewed by everyone in a family
    @Query("SELECT COUNT(u) = COUNT(v) FROM UserFamilyMembership u LEFT JOIN MessageView v " +
           "ON v.userId = u.userId AND v.messageId = :messageId " +
           "WHERE u.familyId = :familyId")
    boolean isMessageViewedByAllFamilyMembers(@Param("messageId") Long messageId, @Param("familyId") Long familyId);
} 