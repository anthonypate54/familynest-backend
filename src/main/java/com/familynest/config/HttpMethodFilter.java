package com.familynest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter to block unwanted HTTP methods before they reach the application.
 * This runs before Spring Security and other filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Run this filter before any other filters
public class HttpMethodFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HttpMethodFilter.class);
    
    // Set of blocked HTTP methods
    private static final Set<String> BLOCKED_METHODS = new HashSet<>(Arrays.asList(
        "PROPFIND",
        "PROPPATCH",
        "MKCOL",
        "COPY",
        "MOVE",
        "LOCK",
        "UNLOCK",
        "TRACE"
    ));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String method = request.getMethod();
        
        // Check if the HTTP method is blocked
        if (BLOCKED_METHODS.contains(method)) {
            logger.debug("Blocked request with method: {}", method);
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
            return;
        }
        
        // Continue with the filter chain for allowed methods
        filterChain.doFilter(request, response);
    }
}
