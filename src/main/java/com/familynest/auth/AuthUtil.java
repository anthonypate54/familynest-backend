package com.familynest.auth;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import com.familynest.repository.UserRepository;
import io.jsonwebtoken.Claims;

/**
 * Authentication utility class that provides methods for password hashing, verification,
 * and validation. This class delegates JWT operations to JwtUtil.
 */
@Component
public class AuthUtil {
    private static final Logger logger = LoggerFactory.getLogger(AuthUtil.class);
    
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final long expirationTime;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    public AuthUtil(
            JwtUtil jwtUtil,
            @Value("${jwt.expiration:86400000}") long expirationTime) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.expirationTime = expirationTime;
     }

    /**
     * Generate a JWT token for a user
     * @param userId User ID
     * @param role User role
     * @return JWT token
     */
    public String generateToken(Long userId, String role) {
        logger.debug("Generating token for userId: {}", userId);
        return jwtUtil.generateAccessToken(userId, role);
    }

    /**
     * Validate a JWT token and return its claims
     * @param token JWT token
     * @return Claims map
     */
    public Map<String, Object> validateTokenAndGetClaims(String token) {
        Claims claims = jwtUtil.validateTokenAndGetClaims(token);
        return claims;
    }

    /**
     * Hash a password using BCrypt
     * @param rawPassword Raw password
     * @return Hashed password
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verify if a raw password matches a hashed password
     * @param rawPassword Raw password
     * @param encodedPassword Hashed password
     * @return true if matches, false otherwise
     */
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * Validates password strength according to security requirements.
     * Requirements:
     * - At least 8 characters long
     * - At least 1 uppercase letter
     * - At least 1 number
     * 
     * @param password The password to validate
     * @return A map with "valid" boolean and "message" string explaining any validation failure
     */
    public Map<String, Object> validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return Map.of(
                "valid", false,
                "message", "Password must be at least 8 characters long"
            );
        }
        
        if (!password.matches(".*[A-Z].*")) {
            return Map.of(
                "valid", false,
                "message", "Password must contain at least one uppercase letter"
            );
        }
        
        if (!password.matches(".*[0-9].*")) {
            return Map.of(
                "valid", false,
                "message", "Password must contain at least one number"
            );
        }
        
        return Map.of("valid", true);
    }

    /**
     * Extract user ID from a JWT token
     * @param token JWT token
     * @return User ID
     */
    public Long extractUserId(String token) {
         return jwtUtil.extractUserId(token);
    }

    /**
     * Validate a JWT token
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
         return jwtUtil.validateToken(token);
    }

    /**
     * Extract user role from a JWT token
     * @param token JWT token
     * @return User role
     */
    public String getUserRole(String token) {
         return jwtUtil.getUserRole(token);
    }
}
