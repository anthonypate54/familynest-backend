package com.familynest.repository;

import com.familynest.model.MessageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageViewRepository extends JpaRepository<MessageView, Long> {
    
    // Family message queries
    @Query("SELECT v FROM MessageView v WHERE v.messageId = :messageId AND v.userId = :userId AND v.messageType = 'family'")
    Optional<MessageView> findByFamilyMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);
    
    @Query("SELECT v FROM MessageView v WHERE v.messageId = :messageId AND v.messageType = 'family'")
    List<MessageView> findByFamilyMessageId(@Param("messageId") Long messageId);
    
    @Query("SELECT COUNT(v) FROM MessageView v WHERE v.messageId = :messageId AND v.messageType = 'family'")
    long countByFamilyMessageId(@Param("messageId") Long messageId);
    
    // DM message queries
    @Query("SELECT v FROM MessageView v WHERE v.dmMessageId = :dmMessageId AND v.userId = :userId AND v.messageType = 'dm'")
    Optional<MessageView> findByDMMessageIdAndUserId(@Param("dmMessageId") Long dmMessageId, @Param("userId") Long userId);
    
    @Query("SELECT v FROM MessageView v WHERE v.dmMessageId = :dmMessageId AND v.messageType = 'dm'")
    List<MessageView> findByDMMessageId(@Param("dmMessageId") Long dmMessageId);
    
    @Query("SELECT COUNT(v) FROM MessageView v WHERE v.dmMessageId = :dmMessageId AND v.messageType = 'dm'")
    long countByDMMessageId(@Param("dmMessageId") Long dmMessageId);
    
    // Generic user queries
    @Query("SELECT v FROM MessageView v WHERE v.userId = :userId")
    List<MessageView> findByUserId(@Param("userId") Long userId);
    
    // Family-specific user queries
    @Query("SELECT v FROM MessageView v JOIN Message m ON v.messageId = m.id " +
           "WHERE v.userId = :userId AND m.familyId = :familyId AND v.messageType = 'family'")
    List<MessageView> findViewsByUserAndFamily(@Param("userId") Long userId, @Param("familyId") Long familyId);
    
    @Query("SELECT COUNT(u) = COUNT(v) FROM UserFamilyMembership u LEFT JOIN MessageView v " +
           "ON v.userId = u.userId AND v.messageId = :messageId AND v.messageType = 'family' " +
           "WHERE u.familyId = :familyId")
    boolean isMessageViewedByAllFamilyMembers(@Param("messageId") Long messageId, @Param("familyId") Long familyId);
    
    // DM conversation queries
    @Query(value = "SELECT v.* FROM message_view v JOIN dm_message dm ON v.dm_message_id = dm.id " +
           "WHERE v.user_id = :userId AND dm.conversation_id = :conversationId AND v.message_type = 'dm'", 
           nativeQuery = true)
    List<MessageView> findViewsByUserAndConversation(@Param("userId") Long userId, @Param("conversationId") Long conversationId);
    
    // Backward compatibility methods (delegate to family message methods)
    default Optional<MessageView> findByMessageIdAndUserId(Long messageId, Long userId) {
        return findByFamilyMessageIdAndUserId(messageId, userId);
    }
    
    default List<MessageView> findByMessageId(Long messageId) {
        return findByFamilyMessageId(messageId);
    }
    
    default long countByMessageId(Long messageId) {
        return countByFamilyMessageId(messageId);
    }
} 