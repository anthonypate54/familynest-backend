package com.familynest.auth;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT utility class for token generation, validation, and parsing.
 * Updated to use Spring Security 6.x compatible approaches.
 */
@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    private final Key key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long legacyExpiration;
    private final String issuer;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access.expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh.expiration:2592000000}") long refreshTokenExpiration,
            @Value("${jwt.expiration:86400000}") long legacyExpiration,
            @Value("${jwt.issuer:familynest-api}") String issuer) {
        
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret key must not be null or empty");
        }
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters long");
        }
        
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.legacyExpiration = legacyExpiration;
        this.issuer = issuer;
        
        logger.info("JwtUtil initialized with access token expiration: {} ms, refresh token expiration: {} ms",
                accessTokenExpiration, refreshTokenExpiration);
    }

    // Legacy method for backward compatibility
    public String generateToken(Long userId, String role) {
        logger.debug("Generating legacy token for userId: {}", userId);
        return generateAccessToken(userId, role);
    }
    
    // Generate access token (short-lived)
    public String generateAccessToken(Long userId, String role) {
        logger.debug("Generating access token for userId: {}", userId);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", role);
        claims.put("type", "access");
        
        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenExpiration, ChronoUnit.MILLIS);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }
    
    // Generate access token with session ID (for single device enforcement)
    public String generateAccessToken(Long userId, String role, String sessionId) {
        logger.debug("Generating access token with session ID for userId: {}", userId);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", role);
        claims.put("type", "access");
        
        if (sessionId != null) {
            claims.put("sid", sessionId);
        }
        
        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenExpiration, ChronoUnit.MILLIS);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }
    
    // Generate refresh token (long-lived)
    public String generateRefreshToken(Long userId) {
        logger.debug("Generating refresh token for userId: {}", userId);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        
        Instant now = Instant.now();
        Instant expiration = now.plus(refreshTokenExpiration, ChronoUnit.MILLIS);
        String tokenId = UUID.randomUUID().toString();
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(tokenId)
                .signWith(key)
                .compact();
    }
    
    // Generate both access and refresh tokens
    public TokenPair generateTokenPair(Long userId, String role) {
        logger.debug("Generating token pair without session ID for userId: {}", userId);
        
        String accessToken = generateAccessToken(userId, role);
        String refreshToken = generateRefreshToken(userId);
        
        return new TokenPair(
                accessToken,
                refreshToken,
                accessTokenExpiration / 1000, // Convert to seconds
                refreshTokenExpiration / 1000  // Convert to seconds
        );
    }
    
    // Generate both access and refresh tokens with session ID
    public TokenPair generateTokenPair(Long userId, String role, String sessionId) {
        logger.debug("Generating token pair with session ID for userId: {}", userId);
        
        String accessToken = generateAccessToken(userId, role, sessionId);
        String refreshToken = generateRefreshToken(userId);
        
        return new TokenPair(
                accessToken,
                refreshToken,
                accessTokenExpiration / 1000, // Convert to seconds
                refreshTokenExpiration / 1000  // Convert to seconds
        );
    }

    // Validate a token and return its claims
    public Claims validateTokenAndGetClaims(String token) throws JwtException {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            throw e; // Re-throw for proper handling
        }
    }

    // Extract user ID from token
    public Long extractUserId(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            String userIdStr = claims.getSubject();
            return Long.parseLong(userIdStr);
        } catch (Exception e) {
            logger.debug("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    // Validate token
    public boolean validateToken(String token) {
        try {
            validateTokenAndGetClaims(token);
            return true;
        } catch (Exception e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // Extract user role from token
    public String getUserRole(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            return claims.get("roles", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Extract session ID from token
    public String getSessionId(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            return claims.get("sid", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract session ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Check if a token is an access token
    public boolean isAccessToken(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            String type = claims.get("type", String.class);
            return "access".equals(type);
        } catch (Exception e) {
            logger.debug("Failed to check token type: {}", e.getMessage());
            return false;
        }
    }
    
    // Check if a token is a refresh token
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            logger.debug("Failed to check token type: {}", e.getMessage());
            return false;
        }
    }
    
    // Extract JTI (JWT ID) from token for tracking
    public String extractTokenId(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            return claims.getId();
        } catch (Exception e) {
            logger.debug("Failed to extract token ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Get token expiration date
    public Date getTokenExpiration(String token) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            return claims.getExpiration();
        } catch (Exception e) {
            logger.debug("Failed to get token expiration: {}", e.getMessage());
            return null;
        }
    }
    
    // Check if a token is expired
    public boolean isTokenExpired(String token) {
        Date expiration = getTokenExpiration(token);
        return expiration != null && expiration.before(new Date());
    }
}
