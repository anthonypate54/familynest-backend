package com.familynest.service;

import com.familynest.auth.JwtUtil;
import com.familynest.auth.TokenPair;
import com.familynest.model.RefreshToken;
import com.familynest.model.User;
import com.familynest.repository.RefreshTokenRepository;
import com.familynest.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RefreshTokenService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Create and store a new refresh token for a user
     */
    public RefreshToken createRefreshToken(Long userId, String refreshTokenString) {
        logger.debug("Creating refresh token for user: {}", userId);
        
        // Get token expiration from JWT
        var expirationDate = jwtUtil.getTokenExpiration(refreshTokenString);
        LocalDateTime expiresAt = expirationDate != null 
            ? expirationDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            : LocalDateTime.now().plusDays(30); // Fallback
        
        RefreshToken refreshToken = new RefreshToken(userId, refreshTokenString, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }
    
    /**
     * Find a valid refresh token by token string
     */
    public Optional<RefreshToken> findValidRefreshToken(String tokenString) {
        logger.debug("Looking for valid refresh token");
        
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(tokenString);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            if (token.isValid()) {
                return Optional.of(token);
            } else {
                logger.debug("Refresh token found but is expired or revoked");
                return Optional.empty();
            }
        }
        
        logger.debug("Refresh token not found");
        return Optional.empty();
    }
    
    /**
     * Refresh access token using a valid refresh token
     * This also rotates the refresh token for security
     */
    public Optional<TokenPair> refreshAccessToken(String refreshTokenString) {
        logger.debug("Attempting to refresh access token");
        
        // Validate refresh token format first
        if (!jwtUtil.validateToken(refreshTokenString) || !jwtUtil.isRefreshToken(refreshTokenString)) {
            logger.debug("Invalid refresh token format");
            return Optional.empty();
        }
        
        // Find the refresh token in database
        Optional<RefreshToken> tokenOpt = findValidRefreshToken(refreshTokenString);
        if (tokenOpt.isEmpty()) {
            logger.debug("Refresh token not found or invalid");
            return Optional.empty();
        }
        
        RefreshToken refreshToken = tokenOpt.get();
        Long userId = refreshToken.getUserId();
        
        // Get user details for role
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            logger.error("User not found for refresh token: {}", userId);
            return Optional.empty();
        }
        
        User user = userOpt.get();
        
        // Generate new token pair
        TokenPair newTokenPair = jwtUtil.generateTokenPair(userId, user.getRole());
        
        // Revoke old refresh token and create new one (token rotation)
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        
        // Store new refresh token
        createRefreshToken(userId, newTokenPair.getRefreshToken());
        
        logger.debug("Successfully refreshed tokens for user: {}", userId);
        return Optional.of(newTokenPair);
    }
    
    /**
     * Revoke a specific refresh token
     */
    public void revokeRefreshToken(String tokenString) {
        logger.debug("Revoking refresh token");
        
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(tokenString);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            token.revoke();
            refreshTokenRepository.save(token);
            logger.debug("Refresh token revoked");
        } else {
            logger.debug("Refresh token not found for revocation");
        }
    }
    
    /**
     * Revoke all refresh tokens for a user (used during logout)
     */
    public void revokeAllUserTokens(Long userId) {
        logger.debug("Revoking all refresh tokens for user: {}", userId);
        refreshTokenRepository.revokeAllTokensForUser(userId);
    }
    
    /**
     * Clean up expired refresh tokens (can be called periodically)
     */
    public void cleanupExpiredTokens() {
        logger.debug("Cleaning up expired refresh tokens");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7); // Keep expired tokens for 7 days for audit
        refreshTokenRepository.deleteExpiredTokensBefore(cutoffDate);
    }
    
    /**
     * Clean up old revoked tokens (can be called periodically)
     */
    public void cleanupRevokedTokens() {
        logger.debug("Cleaning up old revoked refresh tokens");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30); // Keep revoked tokens for 30 days for audit
        refreshTokenRepository.deleteRevokedTokensBefore(cutoffDate);
    }
    
    /**
     * Get count of valid refresh tokens for a user (for rate limiting)
     */
    public long getValidTokenCount(Long userId) {
        return refreshTokenRepository.countValidTokensByUserId(userId, LocalDateTime.now());
    }
    
    /**
     * Check if user has too many refresh tokens (prevent token spam)
     */
    public boolean hasExceededTokenLimit(Long userId) {
        long maxTokensPerUser = 10; // Allow up to 10 active refresh tokens per user
        return getValidTokenCount(userId) >= maxTokensPerUser;
    }
}