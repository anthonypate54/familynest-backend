package com.familynest.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
 * Filter that authenticates requests using JWT tokens from the Authorization header.
 * This filter is registered with Spring Boot to intercept all API requests.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Autowired
    private AuthUtil authUtil;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private TokenBlacklistService tokenBlacklistService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        logger.debug("⚠️ AUTH FILTER - PROCESSING REQUEST FOR URI: {}", path);

           // BYPASS AUTH FOR WEBSOCKET HANDSHAKE
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            logger.debug("⭐⭐⭐ BYPASSING AUTH FOR WEBSOCKET HANDSHAKE: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // BYPASS MAIN AUTH FOR ADMIN ENDPOINTS - they have their own AdminAuthFilter
        if (path.startsWith("/api/admin/")) {
            logger.debug("⭐⭐⭐ BYPASSING MAIN AUTH FOR ADMIN ENDPOINT: {}", path);
            chain.doFilter(request, response);
            return;
        }
        // PUBLIC ENDPOINTS - explicitly without auth
        // These are high-priority bypass paths that should never require auth
        if (path.equals("/api/users/connection-test") || 
            path.equals("/api/users/login") || 
            path.equals("/api/users") ||
            path.equals("/api/users/test") ||
            path.equals("/api/users/forgot-password") ||
            path.equals("/api/users/forgot-username") ||
            path.equals("/api/auth/refresh") ||  // Token refresh endpoint
            path.equals("/reset-password") ||
            path.equals("/reset-password.html") ||
            path.equals("/") ||  // Allow root path access (bots hitting domain)
            path.equals("/error") ||  // Allow error page access
            path.equals("/api/subscription/google-webhook") ||  // Google Play webhook
            path.startsWith("/api/users/password-reset") ||
            path.startsWith("/api/videos/public") ||
            path.startsWith("/api/videos/test") ||
            path.startsWith("/uploads/")) {
            logger.debug("⭐⭐⭐ EXPLICIT PUBLIC BYPASS FOR PATH: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // Print request details for debugging
        logger.debug("⚠️ Request method: {}", request.getMethod());
        logger.debug("⚠️ Request headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.debug("  {}: {}", headerName, request.getHeader(headerName));
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
            path.startsWith("/uploads/thumbnails/") || 
            path.startsWith("/uploads/videos/")) {
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
            logger.debug("⭐⭐⭐ ALLOWING PUBLIC ACCESS TO ENDPOINT: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
     //   logger.error("❌❌❌ ENDPOINT NOT IN PUBLIC WHITELIST: {}", path);

        String authHeader = request.getHeader("Authorization");
         
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.error("❌ No valid Authorization header found: {}", authHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        
        // Check if token is blacklisted
        String tokenId = jwtUtil.extractTokenId(token);
        if (tokenId != null && tokenBlacklistService.isBlacklisted(tokenId)) {
            logger.error("❌ Token is blacklisted: {}", tokenId);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            return;
        }
        
        // Check if token is expired
        if (jwtUtil.isTokenExpired(token)) {
            logger.error("❌ Token is expired");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
            return;
        }
        
        // Validate JWT token format and signature
        try {
            jwtUtil.validateTokenAndGetClaims(token);
        } catch (Exception e) {
            logger.error("❌ Token validation failed for token: {}", token);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token");
            return;
        }

        // Extract user info from token
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.getUserRole(token);
        String tokenSessionId = jwtUtil.getSessionId(token);
        
 
        // SINGLE DEVICE ENFORCEMENT: Validate session ID
        if (tokenSessionId != null) {
            try {
                String currentSessionSql = "SELECT current_session_id FROM app_user WHERE id = ?";
                String dbSessionId = jdbcTemplate.queryForObject(currentSessionSql, String.class, userId);
                
                if (!tokenSessionId.equals(dbSessionId)) {
                    logger.warn("Session invalid: Token session doesn't match database session for user {}", userId);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session invalid - please log in again");
                    return;
                }
             } catch (Exception e) {
                logger.error("Failed to validate session for user {}: {}", userId, e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session validation failed");
                return;
            }
        } else {
            // No session ID in token - reject immediately (single device enforcement)
            logger.warn("Token missing session ID for user {} - session invalid", userId);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session invalid - please log in again");
            return;
        }

        // Set user attributes for the request
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);

        chain.doFilter(request, response);
    }
}
