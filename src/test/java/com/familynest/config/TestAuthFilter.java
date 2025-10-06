package com.familynest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class TestAuthFilter extends OncePerRequestFilter {
    
    // Default values
    private Long testUserId = 1L;
    private String testUserRole = "USER";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain chain) 
            throws ServletException, IOException {
        // Set test user attributes
        request.setAttribute("userId", testUserId);
        
        // Set role
        request.setAttribute("role", testUserRole);
        
        // Continue the filter chain
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Always apply the filter to set test credentials in test mode
        return false;
    }
    
    /**
     * Set the user ID to use for tests
     */
    public void setTestUserId(Long userId) {
        this.testUserId = userId;
    }
    
    /**
     * Set the user role to use for tests
     */
    public void setTestUserRole(String role) {
        this.testUserRole = role;
    }
} 
