package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthUtil authUtil;

    @Value("${payments.mock.enabled:true}")
    private boolean mockPaymentsEnabled;

    @Value("${apple.bundle-id:com.anthony.familynest}")
    private String appleBundleId;

    @Value("${google.package-name:com.anthony.familynest}")
    private String googlePackageName;

    /**
     * Verify Apple App Store purchase receipt
     */
    @PostMapping("/verify-apple")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyApplePurchase(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {
        
        logger.info("🍎 Apple purchase verification request received");
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            String receiptData = (String) request.get("receipt_data");
            String productId = (String) request.get("product_id");
            
            if (receiptData == null || productId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing receipt_data or product_id"));
            }

            if (mockPaymentsEnabled) {
                return mockAppleVerification(userId, receiptData, productId);
            } else {
                return verifyWithApple(userId, receiptData, productId);
            }

        } catch (Exception e) {
            logger.error("Error verifying Apple purchase", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Verification failed"));
        }
    }

    /**
     * Verify Google Play Store purchase receipt
     */
    @PostMapping("/verify-google")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyGooglePurchase(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {
        
        logger.info("🤖 Google purchase verification request received");
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            String purchaseToken = (String) request.get("purchase_token");
            String productId = (String) request.get("product_id");
            
            if (purchaseToken == null || productId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing purchase_token or product_id"));
            }

            if (mockPaymentsEnabled) {
                return mockGoogleVerification(userId, purchaseToken, productId);
            } else {
                return verifyWithGoogle(userId, purchaseToken, productId);
            }

        } catch (Exception e) {
            logger.error("Error verifying Google purchase", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Verification failed"));
        }
    }

    /**
     * Get user's current subscription status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            
            String sql = """
                SELECT subscription_status, trial_end_date, subscription_end_date, 
                       platform, platform_transaction_id, monthly_price
                FROM app_user WHERE id = ?
            """;
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);
            
            // Check if subscription is actually active
            String status = (String) result.get("subscription_status");
            java.sql.Timestamp trialEndTs = (java.sql.Timestamp) result.get("trial_end_date");
            java.sql.Timestamp subscriptionEndTs = (java.sql.Timestamp) result.get("subscription_end_date");
            
            LocalDateTime trialEnd = trialEndTs != null ? trialEndTs.toLocalDateTime() : null;
            LocalDateTime subscriptionEnd = subscriptionEndTs != null ? subscriptionEndTs.toLocalDateTime() : null;
            
            boolean hasActiveAccess = hasActiveSubscriptionAccess(status, trialEnd, subscriptionEnd);
            
            Map<String, Object> response = new HashMap<>(result);
            response.put("has_active_access", hasActiveAccess);
            response.put("is_trial", "trial".equals(status) || "platform_trial".equals(status));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting subscription status", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get status"));
        }
    }

    /**
     * Check if user has active subscription access
     */
    public boolean hasActiveSubscriptionAccess(Long userId) {
        try {
            String sql = """
                SELECT subscription_status, trial_end_date, subscription_end_date
                FROM app_user WHERE id = ?
            """;
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);
            String status = (String) result.get("subscription_status");
            java.sql.Timestamp trialEndTs = (java.sql.Timestamp) result.get("trial_end_date");
            java.sql.Timestamp subscriptionEndTs = (java.sql.Timestamp) result.get("subscription_end_date");
            
            LocalDateTime trialEnd = trialEndTs != null ? trialEndTs.toLocalDateTime() : null;
            LocalDateTime subscriptionEnd = subscriptionEndTs != null ? subscriptionEndTs.toLocalDateTime() : null;
            
            return hasActiveSubscriptionAccess(status, trialEnd, subscriptionEnd);
            
        } catch (Exception e) {
            logger.error("Error checking subscription access for user " + userId, e);
            return false;
        }
    }

    private boolean hasActiveSubscriptionAccess(String status, LocalDateTime trialEnd, LocalDateTime subscriptionEnd) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (status) {
            case "trial":
                return trialEnd != null && trialEnd.isAfter(now);
            case "platform_trial":
            case "active":
                return subscriptionEnd != null && subscriptionEnd.isAfter(now);
            case "expired":
            case "cancelled":
            default:
                return false;
        }
    }

    // MOCK IMPLEMENTATIONS FOR TESTING

    private ResponseEntity<Map<String, Object>> mockAppleVerification(Long userId, String receiptData, String productId) {
        logger.info("🧪 MOCK: Apple verification for user {} product {}", userId, productId);
        
        // Simulate successful Apple verification
        String transactionId = "mock_apple_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Update user subscription in database
        updateUserSubscription(userId, "platform_trial", "APPLE", transactionId, productId);
        
        Map<String, Object> response = Map.of(
            "status", "verified",
            "platform", "APPLE",
            "transaction_id", transactionId,
            "product_id", productId,
            "is_trial", true,
            "message", "Mock Apple verification successful"
        );
        
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> mockGoogleVerification(Long userId, String purchaseToken, String productId) {
        logger.info("🧪 MOCK: Google verification for user {} product {}", userId, productId);
        
        // Simulate successful Google verification
        String transactionId = "mock_google_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Update user subscription in database
        updateUserSubscription(userId, "platform_trial", "GOOGLE", transactionId, productId);
        
        Map<String, Object> response = Map.of(
            "status", "verified",
            "platform", "GOOGLE",
            "transaction_id", transactionId,
            "product_id", productId,
            "is_trial", true,
            "message", "Mock Google verification successful"
        );
        
        return ResponseEntity.ok(response);
    }

    private void updateUserSubscription(Long userId, String status, String platform, String transactionId, String productId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEnd = now.plusDays(30); // 30-day trial
        
        String sql = """
            UPDATE app_user 
            SET subscription_status = ?, 
                platform = ?, 
                platform_transaction_id = ?,
                trial_end_date = ?,
                subscription_start_date = ?,
                updated_at = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql, status, platform, transactionId, trialEnd, now, now, userId);
        
        logger.info("✅ Updated user {} subscription: status={}, platform={}, trial_end={}", 
                   userId, status, platform, trialEnd);
    }

    // REAL API IMPLEMENTATIONS (placeholder for when approved)

    private ResponseEntity<Map<String, Object>> verifyWithApple(Long userId, String receiptData, String productId) {
        // TODO: Implement real Apple receipt verification
        // Call https://buy.itunes.apple.com/verifyReceipt (production)
        // or https://sandbox.itunes.apple.com/verifyReceipt (sandbox)
        
        logger.info("🍎 Real Apple verification not implemented yet");
        return ResponseEntity.badRequest().body(Map.of("error", "Real Apple verification not implemented"));
    }

    private ResponseEntity<Map<String, Object>> verifyWithGoogle(Long userId, String purchaseToken, String productId) {
        // TODO: Implement real Google Play verification
        // Call Google Play Developer API
        
        logger.info("🤖 Real Google verification not implemented yet");
        return ResponseEntity.badRequest().body(Map.of("error", "Real Google verification not implemented"));
    }
}
