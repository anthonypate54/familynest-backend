package com.familynest.controller;

import com.familynest.auth.TokenPair;
import com.familynest.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private RefreshTokenService refreshTokenService;
    
    /**
     * Refresh access token using a valid refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        logger.debug("Received token refresh request");
        
        try {
            String refreshToken = request.get("refreshToken");
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                logger.debug("Missing refresh token in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token is required"));
            }
            
            Optional<TokenPair> tokenPairOpt = refreshTokenService.refreshAccessToken(refreshToken);
            if (tokenPairOpt.isPresent()) {
                TokenPair tokenPair = tokenPairOpt.get();
                logger.debug("Token refresh successful");
                
                return ResponseEntity.ok(Map.of(
                    "accessToken", tokenPair.getAccessToken(),
                    "refreshToken", tokenPair.getRefreshToken(),
                    "accessTokenExpiresIn", tokenPair.getAccessTokenExpiresIn(),
                    "refreshTokenExpiresIn", tokenPair.getRefreshTokenExpiresIn(),
                    "tokenType", "Bearer"
                ));
            } else {
                logger.debug("Token refresh failed - invalid refresh token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
            }
            
        } catch (Exception e) {
            logger.error("Error during token refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Token refresh failed"));
        }
    }
    
    /**
     * Logout - revoke refresh tokens
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> request,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        logger.debug("Received logout request");
        
        try {
            // Try to get refresh token from request body first
            String refreshToken = null;
            if (request != null) {
                refreshToken = request.get("refreshToken");
            }
            
            // If we have a refresh token, revoke it specifically
            if (refreshToken != null && !refreshToken.trim().isEmpty()) {
                refreshTokenService.revokeRefreshToken(refreshToken);
                logger.debug("Revoked specific refresh token");
                
                return ResponseEntity.ok(Map.of("message", "Logout successful"));
            }
            
            // If no refresh token but we have authorization header, try to extract user ID and revoke all tokens
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Note: This would require JWT validation to get user ID
                // For now, just return success since refresh token revocation is the main goal
                logger.debug("Logout without refresh token - cannot revoke tokens");
                return ResponseEntity.ok(Map.of("message", "Logout successful"));
            }
            
            // No tokens provided, but logout request is still considered successful
            logger.debug("Logout request without tokens");
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
            
        } catch (Exception e) {
            logger.error("Error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Logout failed"));
        }
    }
    
    /**
     * Revoke all refresh tokens for the current user (logout from all devices)
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestHeader("Authorization") String authHeader) {
        logger.debug("Received logout-all request");
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization header required"));
            }
            
            // Extract token and validate it to get user ID
            String accessToken = authHeader.substring(7);
            
            // Here we would need to validate the access token and extract user ID
            // For now, return an error as this requires integration with existing auth logic
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "Logout from all devices not yet implemented"));
            
        } catch (Exception e) {
            logger.error("Error during logout-all", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Logout from all devices failed"));
        }
    }
}