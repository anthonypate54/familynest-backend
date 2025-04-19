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

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
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
}