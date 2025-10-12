package com.familynest.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for blacklisting revoked tokens to prevent their use even if they're not expired.
 */
@Service
public class TokenBlacklistService {
    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    // Map of token IDs to their expiration times
    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;

    public TokenBlacklistService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Blacklist a token by its ID and expiration
     *
     * @param tokenId The JWT token ID (jti claim)
     * @param expirationTime When the token expires
     */
    public void blacklistToken(String tokenId, Instant expirationTime) {
        logger.debug("Blacklisting token ID: {}", tokenId);
        blacklistedTokens.put(tokenId, expirationTime);
    }

    /**
     * Blacklist a token by parsing it
     *
     * @param token The JWT token to blacklist
     * @return true if successfully blacklisted, false if token is invalid
     */
    public boolean blacklistToken(String token) {
        try {
            String tokenId = jwtUtil.extractTokenId(token);
            if (tokenId == null) {
                logger.warn("Cannot blacklist token without a token ID (jti claim)");
                return false;
            }

            Instant expiration = jwtUtil.getTokenExpiration(token).toInstant();
            blacklistToken(tokenId, expiration);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to blacklist token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a token is blacklisted
     *
     * @param tokenId The JWT token ID
     * @return true if blacklisted, false otherwise
     */
    public boolean isBlacklisted(String tokenId) {
        return blacklistedTokens.containsKey(tokenId);
    }

    /**
     * Check if a token is blacklisted by parsing it
     *
     * @param token The JWT token
     * @return true if blacklisted or invalid, false if valid and not blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String tokenId = jwtUtil.extractTokenId(token);
            if (tokenId == null) {
                // If we can't extract the token ID, consider it invalid/blacklisted
                return true;
            }
            return isBlacklisted(tokenId);
        } catch (Exception e) {
            // If we can't parse the token, consider it invalid/blacklisted
            return true;
        }
    }

    /**
     * Clean up expired blacklisted tokens
     * Runs every hour by default
     */
    @Scheduled(fixedRateString = "${jwt.blacklist.cleanup.interval:3600000}")
    public void cleanupExpiredTokens() {
        logger.debug("Cleaning up expired blacklisted tokens");
        Instant now = Instant.now();

        // Remove tokens that have expired
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

        logger.debug("Blacklist cleanup complete. Remaining tokens: {}", blacklistedTokens.size());
    }
}
