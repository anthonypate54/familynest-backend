package com.familynest.repository;

import com.familynest.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    /**
     * Find a refresh token by its token string
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * Find all valid (non-revoked, non-expired) refresh tokens for a user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    
    /**
     * Find all refresh tokens for a user (including expired/revoked)
     */
    List<RefreshToken> findByUserId(Long userId);
    
    /**
     * Revoke all refresh tokens for a user (used during logout)
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId")
    void revokeAllTokensForUser(@Param("userId") Long userId);
    
    /**
     * Delete expired refresh tokens (cleanup task)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoffDate")
    void deleteExpiredTokensBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Delete revoked refresh tokens older than specified date (cleanup task)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.isRevoked = true AND rt.createdAt < :cutoffDate")
    void deleteRevokedTokensBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Count valid refresh tokens for a user (for rate limiting)
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    long countValidTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}