package com.familynest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class TestAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain chain) 
            throws ServletException, IOException {
        // Set test user attributes
        request.setAttribute("userId", 1L);
        
        // Set ADMIN role for create-family endpoint
        if (request.getRequestURI().contains("/create-family")) {
            request.setAttribute("role", "ADMIN");
        } else {
            request.setAttribute("role", "USER");
        }
        
        // Continue the filter chain
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Don't filter login and registration endpoints
        String path = request.getRequestURI();
        return path.equals("/api/users/login") || 
               path.equals("/api/users") || 
               path.equals("/api/users/test");
    }
} 