package com.familynest.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Autowired
    private AuthUtil authUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        logger.debug("Processing request for URI: {}", request.getRequestURI());
        logger.debug("Request method: {}", request.getMethod());
        logger.debug("Request headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.debug("  {}: {}", headerName, request.getHeader(headerName));
        }
        
        String path = request.getRequestURI();
        
        // Check if this is a photo request
        if (path.startsWith("/api/users/photos/")) {
            logger.debug("Allowing photo request for path: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // Check other public endpoints
        if (path.equals("/api/users/login") || path.equals("/api/users") || path.equals("/api/users/test")) {
            logger.debug("Allowing public request to: {}", path);
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.debug("No valid Authorization header found: {}", authHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        if (!authUtil.validateToken(token)) {
            logger.debug("Token validation failed for token: {}", token);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token");
            return;
        }

        Long userId = authUtil.extractUserId(token);
        String role = authUtil.getUserRole(token);
        logger.debug("Extracted userId: {}, role: {}", userId, role);

        // Set user attributes for the request
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        logger.debug("Valid token for userId: {}, role: {}, proceeding to controller", userId, role);

        chain.doFilter(request, response);
    }
}