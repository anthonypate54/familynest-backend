package com.familynest.auth;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import com.familynest.model.User;
import com.familynest.repository.UserRepository;

@Component
public class AuthUtil {
    private static final Logger logger = LoggerFactory.getLogger(AuthUtil.class);
    
    private final Key key;
    private final PasswordEncoder passwordEncoder;
    private final long expirationTime;

    @Autowired
    private UserRepository userRepository; // Inject UserRepository

    public AuthUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationTime) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret key must not be null or empty");
        }
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters long");
        }
        try {
            this.key = Keys.hmacShaKeyFor(secret.getBytes());
            this.passwordEncoder = new BCryptPasswordEncoder();
            this.expirationTime = expirationTime;
            logger.info("AuthUtil initialized with expiration time: {} ms", expirationTime);
        } catch (Exception e) {
            logger.error("Failed to initialize AuthUtil: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize AuthUtil", e);
        }
    }

    public String generateToken(Long userId, String role) {
        logger.debug("Generating token for userId: {}", userId);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);
        logger.debug("Token will expire at: {} (in {} ms)", expiration, expirationTime);
        
        String token = Jwts.builder()
            .setSubject(userId.toString())
            .claim("userId", userId.toString())  // Duplicate for compatibility
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiration)
            .signWith(key)
            .compact();
            
        logger.debug("Generated token: {} (length: {})", 
                   token.substring(0, Math.min(10, token.length())) + "...", 
                   token.length());
        return token;
    }

    public Map<String, Object> validateTokenAndGetClaims(String token) {
        logger.debug("Extracting claims from token starting with: {}...", 
                   token.substring(0, Math.min(10, token.length())));
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            if (claims.getExpiration().before(new Date())) {
                logger.error("Token is expired! Expiration: {}, Current time: {}", 
                           claims.getExpiration(), new Date());
                throw new ExpiredJwtException(null, claims, "Token is expired");
            }
            
            return claims;
        } catch (Exception e) {
            logger.error("Failed to extract claims from token: {}", e.getMessage());
            throw e;
        }
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public Long extractUserId(String token) {
        logger.debug("Extracting userId from token: {}", maskToken(token));
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            String userIdStr;
            
            // Try to get userId from two places for compatibility
            // First from "userId" claim
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                userIdStr = userIdObj.toString();
            } else {
                // Then fall back to "sub" (subject)
                userIdStr = claims.getSubject();
            }
            
            logger.debug("Extracted userId: {} from token", userIdStr);
            return Long.parseLong(userIdStr);
        } catch (ExpiredJwtException e) {
            logger.error("Token expired: {}", e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            logger.error("Unsupported JWT: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            logger.error("Malformed JWT: {}", e.getMessage());
            return null;
        } catch (SignatureException e) {
            logger.error("JWT signature verification failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        logger.error("🔍 Validating token: {}", maskToken(token));
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            // Check if token has expired
            Date expiration = claims.getExpiration();
            Date now = new Date();
            if (expiration.before(now)) {
                logger.error("🔍 Token has expired! Expiration: {}, Current time: {}", 
                          expiration, now);
                return false;
            }
            
            // Get key claims for logging
            String subject = claims.getSubject();
            Object userId = claims.get("userId");
            Object role = claims.get("role");
            Date issuedAt = claims.getIssuedAt();
            
            logger.error("🔍 Token is valid! Subject: {}, UserId: {}, Role: {}, IssuedAt: {}, Expiration: {}", 
                      subject, userId, role, issuedAt, expiration);
            return true;
        } catch (ExpiredJwtException e) {
            logger.error("🔍 Token expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            logger.error("🔍 Unsupported JWT: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.error("🔍 Malformed JWT: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            logger.error("🔍 JWT signature verification failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("🔍 Token validation failed: {} ({})", e.getMessage(), e.getClass().getName());
            return false;
        }
    }

    public String getUserRole(String token) {
        logger.debug("Extracting role from token: {}", maskToken(token));
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            String role = claims.get("role", String.class);
            logger.debug("Extracted role: {} from token", role);
            return role;
        } catch (Exception e) {
            logger.error("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }
    
    // Helper method to partially mask token for logging
    private String maskToken(String token) {
        if (token == null) return "null";
        if (token.length() <= 10) return token.substring(0, 3) + "...";
        return token.substring(0, 5) + "..." + token.substring(token.length() - 5);
    }
}