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

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Autowired
    private AuthUtil authUtil;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        logger.debug("⚠️ AUTH FILTER - PROCESSING REQUEST FOR URI: {}", path);

           // BYPASS AUTH FOR WEBSOCKET HANDSHAKE
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            logger.error("⭐⭐⭐ BYPASSING AUTH FOR WEBSOCKET HANDSHAKE: {}", path);
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
            path.startsWith("/api/users/password-reset") ||
            path.startsWith("/api/videos/public") ||
            path.startsWith("/api/videos/test") ||
            path.startsWith("/uploads/")) {
            logger.error("⭐⭐⭐ EXPLICIT PUBLIC BYPASS FOR PATH: {}", path);
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
            logger.error("⭐⭐⭐ ALLOWING PUBLIC ACCESS TO ENDPOINT: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
     //   logger.error("❌❌❌ ENDPOINT NOT IN PUBLIC WHITELIST: {}", path);

        String authHeader = request.getHeader("Authorization");
        logger.debug("🔑 Auth header: {}", authHeader);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.error("❌ No valid Authorization header found: {}", authHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        logger.debug("🔑 Token: {}", token);
        
        // Validate JWT token format and signature
        if (!jwtUtil.validateToken(token)) {
            logger.error("❌ Token validation failed for token: {}", token);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token");
            return;
        }

        // Extract user info from token
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.getUserRole(token);
        String tokenSessionId = jwtUtil.getSessionId(token);
        logger.debug("✅ Token valid! Extracted userId: {}, role: {}, sessionId: {}", userId, role, tokenSessionId);

        // SINGLE DEVICE ENFORCEMENT: Validate session ID
        if (tokenSessionId != null) {
            try {
                String currentSessionSql = "SELECT current_session_id FROM app_user WHERE id = ?";
                String dbSessionId = jdbcTemplate.queryForObject(currentSessionSql, String.class, userId);
                
                if (!tokenSessionId.equals(dbSessionId)) {
                    logger.warn("🚫 SESSION_INVALID: Token session {} doesn't match DB session {} for user {}", 
                               tokenSessionId, dbSessionId, userId);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session invalid - please log in again");
                    return;
                }
                logger.debug("✅ SESSION_VALID: Session ID matches for user {}", userId);
            } catch (Exception e) {
                logger.error("❌ SESSION_CHECK_ERROR: Failed to validate session for user {}: {}", userId, e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session validation failed");
                return;
            }
        } else {
            // Legacy token without session ID - allow for backward compatibility but log it
            logger.warn("⚠️ LEGACY_TOKEN: No session ID found in token for user {} - consider forcing re-login", userId);
        }

        // Set user attributes for the request
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        logger.debug("Valid token and session for userId: {}, role: {}, proceeding to controller", userId, role);

        chain.doFilter(request, response);
    }
}
