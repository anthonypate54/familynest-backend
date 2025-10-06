package com.familynest.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "user2123";
        String hash = encoder.encode(password);
        System.out.println("BCrypt hash for '" + password + "': " + hash);
        
        // Also print the fixed hash we know should work for testing
        System.out.println("Fixed hash: $2a$10$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.");
    }
} 
