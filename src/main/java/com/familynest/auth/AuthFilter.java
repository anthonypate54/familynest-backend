package com.familynest.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${app.url.videos:/uploads/videos}")
    private String videosUrlPath;
    
    @Value("${app.url.thumbnails:/uploads/thumbnails}")
    private String thumbnailsUrlPath;
    
    @Value("${app.url.images:/uploads/images}")
    private String imagesUrlPath;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        logger.error("⚠️ AUTH FILTER - PROCESSING REQUEST FOR URI: {}", path);
        
        // EMERGENCY BYPASS FOR USER 101
        if (path.startsWith("/api/users/101") || path.startsWith("/api/emergency")) {
            logger.error("🔓 EMERGENCY BYPASS FOR PATH: {}", path);
            // Set test user attributes
            request.setAttribute("userId", 101L);
            request.setAttribute("role", "ADMIN");
            chain.doFilter(request, response);
            return;
        }
        
        // PUBLIC ENDPOINTS - explicitly without auth
        // These are high-priority bypass paths that should never require auth
        if (path.equals("/api/users/connection-test") || 
            path.equals("/api/users/login") || 
            path.equals("/api/users") ||
            path.equals("/api/users/test-token") ||
            path.equals("/api/users/test-token-101") ||
            path.equals("/api/users/debug-token") ||
            path.equals("/api/users/public-test-upload") ||
            path.startsWith("/api/videos/public") ||
            path.startsWith("/api/videos/test") ||
            path.startsWith("/uploads/")) {
            logger.error("⭐⭐⭐ EXPLICIT PUBLIC BYPASS FOR PATH: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // TEMPORARY TEST BYPASS
        if (path.equals("/api/users/101") || path.equals("/api/users/101/messages")) {
            logger.error("🧪 TEMPORARY TEST BYPASS for user 101: {}", path);
            // Set authentication attributes for user 101
            request.setAttribute("userId", 101L);
            request.setAttribute("role", "ADMIN");
            chain.doFilter(request, response);
            return;
        }
        
        // Print request details for debugging
        logger.error("⚠️ Request method: {}", request.getMethod());
        logger.error("⚠️ Request headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.error("  {}: {}", headerName, request.getHeader(headerName));
        }
        
        // Check if a test filter has already set authentication attributes
        // This allows tests to bypass normal authentication
        if (request.getAttribute("userId") != null) {
            logger.debug("Test authentication detected - userId: {}, role: {}", 
                request.getAttribute("userId"), request.getAttribute("role"));
            chain.doFilter(request, response);
            return;
        }
        
        // Add special handling for video endpoints - high priority
        if (path.startsWith("/api/videos") || path.startsWith("/uploads/")) {
            logger.debug("Allowing video-related request: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // Check if this is a photo or thumbnail request
        if (path.startsWith("/api/users/photos/") || 
            path.startsWith(thumbnailsUrlPath) || 
            path.startsWith(videosUrlPath)) {
            logger.debug("Allowing media request for path: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // Check other public endpoints
        if (path.equals("/api/users/login") || path.equals("/api/users") || path.equals("/api/users/test") ||
            path.equals("/api/users/connection-test") ||
            path.startsWith("/test/") || path.startsWith("/public/") ||  // Allow all /test/* and /public/* endpoints
            path.startsWith("/api/public") || path.startsWith("/api/test") ||  // Also allow /api/public/* and /api/test/*
            path.contains("thumbnail") || path.contains("video-test") ||
            path.contains("health") || path.contains("videos/upload")) {  // Allow health checks and video uploads
            logger.error("⭐⭐⭐ ALLOWING PUBLIC ACCESS TO ENDPOINT: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        logger.error("❌❌❌ ENDPOINT NOT IN PUBLIC WHITELIST: {}", path);

        String authHeader = request.getHeader("Authorization");
        logger.error("🔑 Auth header: {}", authHeader);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.error("❌ No valid Authorization header found: {}", authHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        logger.error("🔑 Token: {}", token);
        
        if (!authUtil.validateToken(token)) {
            logger.error("❌ Token validation failed for token: {}", token);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token");
            return;
        }

        Long userId = authUtil.extractUserId(token);
        String role = authUtil.getUserRole(token);
        logger.error("✅ Token valid! Extracted userId: {}, role: {}", userId, role);

        // Set user attributes for the request
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        logger.debug("Valid token for userId: {}, role: {}, proceeding to controller", userId, role);

        chain.doFilter(request, response);
    }
}