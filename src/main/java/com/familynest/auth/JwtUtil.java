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
import org.springframework.beans.factory.annotation.Value;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    private final Key key;
    private final long expirationTime;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                  @Value("${jwt.expiration:86400000}") long expirationTime) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationTime = expirationTime;
    }

    public String generateToken(Long userId, String role) {
        logger.debug("Generating token for userId: {}", userId);
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role)
            .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
            .signWith(key)
            .compact();
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
            logger.error("🔍 JWT DEBUG - Validating token starting with: {}...", token.substring(0, Math.min(20, token.length())));
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            // Print all claims for debugging
            logger.error("🔍 JWT DEBUG - Token is valid. Claims: {}", claims);
            logger.error("🔍 JWT DEBUG - Subject: {}, Role: {}, Expiration: {}", 
                      claims.getSubject(), claims.get("role"), claims.getExpiration());
                
            // Check if token has expired
            if (claims.getExpiration().before(new Date())) {
                logger.error("🔍 JWT DEBUG - Token has expired! Expiration: {}, Current time: {}", 
                          claims.getExpiration(), new Date());
                return false;
            }
            logger.debug("Token validated successfully");
            return true;
        } catch (Exception e) {
            logger.error("🔍 JWT DEBUG - Token validation failed: {} ({})", e.getMessage(), e.getClass().getName());
            logger.error("🔍 JWT DEBUG - Token: {}", token);
            // Print a few characters of the token to help identify which token it is
            if (token.length() > 10) {
                logger.error("🔍 JWT DEBUG - Token starts with: {}..., ends with: ...{}", 
                          token.substring(0, 10), token.substring(token.length() - 10));
            }
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
} 