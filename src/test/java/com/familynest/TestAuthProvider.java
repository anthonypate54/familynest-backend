package com.familynest;

import com.familynest.auth.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Utility class for generating test authentication tokens
 */
@Service
@Profile("testdb")
public class TestAuthProvider {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Generate a valid JWT token for the test user
     * @param userId The user ID to generate a token for
     * @return A valid JWT token
     */
    public String generateTestToken(Long userId) {
        return jwtUtil.generateToken(userId, "USER");
    }
} 
