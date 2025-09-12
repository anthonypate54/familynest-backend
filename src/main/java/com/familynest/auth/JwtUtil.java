package com.familynest.auth;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    private final Key key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long legacyExpiration; // For backward compatibility

    public JwtUtil(@Value("${jwt.secret}") String secret,
                  @Value("${jwt.access.expiration:3600000}") long accessTokenExpiration,
                  @Value("${jwt.refresh.expiration:2592000000}") long refreshTokenExpiration,
                  @Value("${jwt.expiration:86400000}") long legacyExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.legacyExpiration = legacyExpiration;
    }

    // Legacy method for backward compatibility
    public String generateToken(Long userId, String role) {
        logger.debug("Generating legacy token for userId: {}", userId);
        return generateAccessToken(userId, role);
    }
    
    // Generate access token (short-lived)
    public String generateAccessToken(Long userId, String role) {
        logger.debug("Generating access token for userId: {}", userId);
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role)
            .claim("type", "access")
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(key)
            .compact();
    }
    
    // Generate access token with session ID (for single device enforcement)
    public String generateAccessToken(Long userId, String role, String sessionId) {
        logger.debug("Generating access token with session ID for userId: {}", userId);
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role)
            .claim("type", "access")
            .claim("sessionId", sessionId)
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(key)
            .compact();
    }
    
    // Generate refresh token (long-lived)
    public String generateRefreshToken(Long userId) {
        logger.debug("Generating refresh token for userId: {}", userId);
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("type", "refresh")
            .claim("jti", UUID.randomUUID().toString()) // Unique token ID
            .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(key)
            .compact();
    }
    
    // Generate both access and refresh tokens
    public TokenPair generateTokenPair(Long userId, String role) {
        logger.debug("Generating token pair for userId: {}", userId);
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

    public Map<String, Object> validateTokenAndGetClaims(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        return claims;
    }

    public Long extractUserId(String token) {
        logger.debug("Extracting userId from token: {}", token);
        try {
            String userIdStr = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
            return Long.parseLong(userIdStr);
        } catch (Exception e) {
            logger.debug("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        logger.debug("Validating token: {}", token);
        try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
            logger.debug("Token validated successfully");
            return true;
        } catch (Exception e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUserRole(String token) {
        logger.debug("Extracting role from token: {}", token);
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }
    
    public String getSessionId(String token) {
        logger.debug("Extracting session ID from token: {}", token);
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("sessionId", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract session ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Check if token is an access token
    public boolean isAccessToken(String token) {
        try {
            String type = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("type", String.class);
            return "access".equals(type);
        } catch (Exception e) {
            logger.debug("Failed to check token type: {}", e.getMessage());
            return false;
        }
    }
    
    // Check if token is a refresh token
    public boolean isRefreshToken(String token) {
        try {
            String type = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            logger.debug("Failed to check token type: {}", e.getMessage());
            return false;
        }
    }
    
    // Extract JTI (JWT ID) from refresh token for tracking
    public String extractJti(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("jti", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract JTI from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Get token expiration date
    public Date getTokenExpiration(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        } catch (Exception e) {
            logger.debug("Failed to extract expiration from token: {}", e.getMessage());
            return null;
        }
    }
} 