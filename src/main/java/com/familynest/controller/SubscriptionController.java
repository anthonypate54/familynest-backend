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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription")
@SuppressWarnings("unchecked") // Suppress unchecked warnings for JDBC operations
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

    // Global subscription pricing - should be managed by admin interface
    @Value("${subscription.monthly.price:2.99}")
    private double monthlyPrice;

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
                       platform, platform_transaction_id
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
            // Use global pricing from application properties
            response.put("monthly_price", monthlyPrice);
            
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
        
        logger.debug("🔍 Checking access: status={}, trialEnd={}, subscriptionEnd={}, now={}", 
                    status, trialEnd, subscriptionEnd, now);
        
        switch (status) {
            case "trial":
            case "platform_trial":
                // For trials, check trial_end_date using DATE-ONLY comparison (ignore time)
                if (trialEnd == null) {
                    logger.debug("❌ Trial access: no trial end date");
                    return false;
                }
                
                // Compare dates only - if trial ends today or later, still active
                boolean trialActive = !trialEnd.toLocalDate().isBefore(now.toLocalDate());
                logger.debug("📅 Trial access (date-only): trialDate={}, todayDate={}, active={}", 
                           trialEnd.toLocalDate(), now.toLocalDate(), trialActive);
                return trialActive;
                
            case "active":
                // For paid subscriptions, if no end date specified, assume ongoing access
                // This handles platform-managed subscriptions where we don't track end dates
                if (subscriptionEnd == null) {
                    logger.debug("✅ Active subscription with no end date - granting access");
                    return true;
                }
                // If end date is specified, check it using date-only comparison
                boolean subscriptionActive = !subscriptionEnd.toLocalDate().isBefore(now.toLocalDate());
                logger.debug("📅 Paid subscription access (date-only): subDate={}, todayDate={}, active={}", 
                           subscriptionEnd.toLocalDate(), now.toLocalDate(), subscriptionActive);
                return subscriptionActive;
                
            case "past_due":
                // GRACE PERIOD: When billing fails, Google/Apple gives users time to fix payment
                // During grace period (usually 7-16 days), user still has access but should see warnings
                if (subscriptionEnd == null) {
                    logger.debug("⚠️ Past due subscription with no end date - granting grace access");
                    return true; // Grant access during grace period
                }
                
                // Check if still within grace period (assume 7 days grace)
                LocalDateTime graceEnd = subscriptionEnd.plusDays(7);
                boolean inGracePeriod = !graceEnd.toLocalDate().isBefore(now.toLocalDate());
                logger.debug("⚠️ Past due access check: graceEnd={}, todayDate={}, inGrace={}", 
                           graceEnd.toLocalDate(), now.toLocalDate(), inGracePeriod);
                return inGracePeriod;
                
            case "cancelled":
                // Cancelled users keep access until their paid period ends
                if (subscriptionEnd == null) {
                    logger.debug("❌ Cancelled subscription with no end date - denying access");
                    return false;
                }
                boolean cancelledActive = !subscriptionEnd.toLocalDate().isBefore(now.toLocalDate());
                logger.debug("📅 Cancelled subscription access (date-only): subDate={}, todayDate={}, active={}", 
                           subscriptionEnd.toLocalDate(), now.toLocalDate(), cancelledActive);
                return cancelledActive;
                
            case "expired":
                logger.debug("❌ Subscription expired - denying access");
                return false;
                
            default:
                logger.warn("⚠️ Unknown subscription status '{}' - denying access", status);
                return false;
        }
    }

    // MOCK IMPLEMENTATIONS FOR TESTING

    private ResponseEntity<Map<String, Object>> mockAppleVerification(Long userId, String receiptData, String productId) {
        logger.info("🧪 MOCK: Apple verification for user {} product {}", userId, productId);
        
        // Simulate successful Apple verification
        String transactionId = "mock_apple_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Update user subscription in database
        updateUserSubscription(userId, "active", "APPLE", transactionId, productId);
        
        Map<String, Object> response = Map.of(
            "status", "verified",
            "platform", "APPLE",
            "transaction_id", transactionId,
            "product_id", productId,
            "is_trial", false,
            "message", "Mock Apple verification successful"
        );
        
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> mockGoogleVerification(Long userId, String purchaseToken, String productId) {
        logger.info("🧪 MOCK: Google verification for user {} product {}", userId, productId);
        
        // Simulate successful Google verification
        String transactionId = "mock_google_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Update user subscription in database
        updateUserSubscription(userId, "active", "GOOGLE", transactionId, productId);
        
        Map<String, Object> response = Map.of(
            "status", "verified",
            "platform", "GOOGLE",
            "transaction_id", transactionId,
            "product_id", productId,
            "is_trial", false,
            "message", "Mock Google verification successful"
        );
        
        return ResponseEntity.ok(response);
    }

    private void updateUserSubscription(Long userId, String status, String platform, String transactionId, String productId) {
        // CRITICAL: Use UTC for all subscription time handling
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime trialEnd = now.plusDays(30); // 30-day trial
        LocalDateTime subscriptionEnd = now.plusDays(30); // Monthly subscription period
        
        String sql = """
            UPDATE app_user 
            SET subscription_status = ?, 
                platform = ?, 
                platform_transaction_id = ?,
                trial_end_date = ?,
                subscription_start_date = ?,
                subscription_end_date = ?,
                updated_at = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql, status, platform, transactionId, trialEnd, now, subscriptionEnd, now, userId);
        
        // Record the transaction in payment_transactions table
        boolean isTrial = "trial".equals(status) || "platform_trial".equals(status);
        recordPaymentTransaction(userId, platform, transactionId, productId, isTrial);
        
        logger.info("✅ Updated user {} subscription: status={}, platform={}, trial_end={}", 
                   userId, status, platform, trialEnd);
    }
    
    private void recordPaymentTransaction(Long userId, String platform, String transactionId, String productId, boolean isTrial) {
        try {
            String sql = """
                INSERT INTO payment_transactions 
                (user_id, platform, amount, description, platform_transaction_id, product_id, status, transaction_date)
                VALUES (?, ?, ?, ?, ?, ?, 'completed', ?)
            """;
            
            double amount = isTrial ? 0.00 : monthlyPrice;
            String description = isTrial ? 
                "30-day free trial started" : 
                "Premium subscription - " + productId;
            
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            jdbcTemplate.update(sql, userId, platform, amount, description, transactionId, productId, now);
            
            logger.info("💰 Recorded payment transaction for user {}: ${} - {}", userId, amount, description);
            
        } catch (Exception e) {
            logger.error("❌ Failed to record payment transaction for user {}", userId, e);
            // Don't fail the whole subscription process if transaction recording fails
        }
    }

    /**
     * Get user's payment history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getPaymentHistory(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            
            // Get user's current subscription info
            String userSql = """
                SELECT subscription_status, trial_start_date, trial_end_date, 
                       subscription_start_date, subscription_end_date, monthly_price, platform
                FROM app_user WHERE id = ?
            """;
            
            Map<String, Object> userInfo = jdbcTemplate.queryForMap(userSql, userId);
            
            // Get transaction history
            String transactionSql = """
                SELECT transaction_date, amount, description, status, platform, product_id, created_at
                FROM payment_transactions 
                WHERE user_id = ? 
                ORDER BY transaction_date DESC 
                LIMIT 50
            """;
            
            List<Map<String, Object>> transactions = jdbcTemplate.queryForList(transactionSql, userId);
            
            // Build response
            Map<String, Object> response = new HashMap<>(userInfo);
            response.put("transactions", transactions);
            
            // Calculate has_active_access
            String status = (String) userInfo.get("subscription_status");
            java.sql.Timestamp trialEndTs = (java.sql.Timestamp) userInfo.get("trial_end_date");
            java.sql.Timestamp subscriptionEndTs = (java.sql.Timestamp) userInfo.get("subscription_end_date");
            
            LocalDateTime trialEnd = trialEndTs != null ? trialEndTs.toLocalDateTime() : null;
            LocalDateTime subscriptionEnd = subscriptionEndTs != null ? subscriptionEndTs.toLocalDateTime() : null;
            
            boolean hasActiveAccess = hasActiveSubscriptionAccess(status, trialEnd, subscriptionEnd);
            response.put("has_active_access", hasActiveAccess);
            response.put("is_trial", "trial".equals(status) || "platform_trial".equals(status));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting payment history", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get payment history"));
        }
    }

    /**
     * Cancel user subscription
     */
    @PostMapping("/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            
            // Update subscription status to cancelled
            String sql = """
                UPDATE app_user 
                SET subscription_status = 'cancelled',
                    updated_at = ?
                WHERE id = ?
            """;
            
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            jdbcTemplate.update(sql, now, userId);
            
            // Record the cancellation as a transaction
            recordCancellationTransaction(userId);
            
            logger.info("✅ User {} cancelled their subscription", userId);
            
            return ResponseEntity.ok(Map.of(
                "status", "cancelled",
                "message", "Subscription cancelled successfully",
                "effective_date", LocalDateTime.now(java.time.ZoneOffset.UTC).toString()
            ));
            
        } catch (Exception e) {
            logger.error("Error cancelling subscription", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to cancel subscription"));
        }
    }

    /**
     * Google Play Real-time Developer Notifications webhook
     * This is called by Google Play when subscription events occur (renewals, cancellations, etc.)
     * URL to configure in Google Play Console: https://yourdomain.com/api/subscription/google-webhook
     */
    @PostMapping("/google-webhook")
    public ResponseEntity<Map<String, Object>> handleGoogleWebhook(
            @RequestBody Map<String, Object> notificationData) {
        
        try {
            logger.info("🔔 Google Play webhook received: {}", notificationData);
            
            // Extract Google Play notification data
            // Real format: {"version":"1.0","packageName":"com.anthony.familynest","eventTimeMillis":"...","subscriptionNotification":{...}}
            String packageName = (String) notificationData.get("packageName");
            Object notificationObj = notificationData.get("subscriptionNotification");
            Map<String, Object> subscriptionNotification = (notificationObj instanceof Map) ? (Map<String, Object>) notificationObj : null;
            
            if (subscriptionNotification == null) {
                logger.warn("⚠️ No subscriptionNotification in webhook data");
                return ResponseEntity.ok(Map.of("status", "ignored"));
            }
            
            String purchaseToken = (String) subscriptionNotification.get("purchaseToken");
            Integer notificationType = (Integer) subscriptionNotification.get("notificationType");
            String subscriptionId = (String) subscriptionNotification.get("subscriptionId");
            
            // Find user by purchase token
            String findUserSql = "SELECT id, username FROM app_user WHERE platform_transaction_id = ?";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(findUserSql, purchaseToken);
            
            if (users.isEmpty()) {
                logger.warn("⚠️ No user found for purchase token: {}", purchaseToken);
                return ResponseEntity.ok(Map.of("status", "user_not_found"));
            }
            
            Long userId = ((Number) users.get(0).get("id")).longValue();
            
            // Handle different notification types
            switch (notificationType) {
                case 2: // SUBSCRIPTION_RENEWED
                    logger.info("📅 Subscription renewed for user {}", userId);
                    recordPaymentTransaction(userId, "GOOGLE", purchaseToken, subscriptionId, false);
                    break;
                case 3: // SUBSCRIPTION_CANCELED
                    logger.info("🚫 Subscription canceled by Google for user {}", userId);
                    recordCancellationTransaction(userId);
                    break;
                case 4: // SUBSCRIPTION_PURCHASED
                    logger.info("🛒 New subscription purchased for user {}", userId);
                    recordPaymentTransaction(userId, "GOOGLE", purchaseToken, subscriptionId, false);
                    break;
                case 12: // SUBSCRIPTION_EXPIRED
                    logger.info("⏰ Subscription expired for user {}", userId);
                    updateUserSubscriptionStatus(userId, "expired");
                    break;
                default:
                    logger.info("ℹ️ Unhandled notification type {} for user {}", notificationType, userId);
            }
            
            return ResponseEntity.ok(Map.of("status", "processed", "notificationType", notificationType));
            
        } catch (Exception e) {
            logger.error("Error processing Google webhook", e);
            return ResponseEntity.ok(Map.of("status", "error")); // Always return 200 to Google
        }
    }

    /**
     * Handle billing failure notifications from Google Play / Apple App Store
     * This endpoint would be called by webhooks when credit card fails
     */
    @PostMapping("/billing-failure")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleBillingFailure(
            @RequestBody Map<String, Object> billingEvent) {
        
        try {
            String platform = (String) billingEvent.get("platform"); // "GOOGLE" or "APPLE"
            String transactionId = (String) billingEvent.get("transaction_id");
            String failureReason = (String) billingEvent.get("failure_reason");
            
            logger.warn("💳 BILLING FAILURE: platform={}, transactionId={}, reason={}", 
                       platform, transactionId, failureReason);
            
            // Find user by platform transaction ID
            String findUserSql = """
                SELECT id, username, email, subscription_status, fcm_token
                FROM app_user 
                WHERE platform_transaction_id = ?
            """;
            
            List<Map<String, Object>> users = jdbcTemplate.queryForList(findUserSql, transactionId);
            
            if (users.isEmpty()) {
                logger.warn("⚠️ No user found for failed transaction: {}", transactionId);
                return ResponseEntity.ok(Map.of("status", "ignored", "message", "User not found"));
            }
            
            Map<String, Object> user = users.get(0);
            Long userId = ((Number) user.get("id")).longValue();
            String currentStatus = (String) user.get("subscription_status");
            String fcmToken = (String) user.get("fcm_token");
            
            // Transition from 'active' to 'past_due' (grace period)
            if ("active".equals(currentStatus)) {
                String updateSql = """
                    UPDATE app_user 
                    SET subscription_status = 'past_due',
                        updated_at = ?
                    WHERE id = ?
                """;
                
                LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
                jdbcTemplate.update(updateSql, now, userId);
                
                // Record failed transaction
                recordFailedTransaction(userId, platform, transactionId, failureReason);
                
                // Send push notification to user about billing issue
                sendBillingFailureNotification(userId, fcmToken, failureReason);
                
                logger.info("⚠️ User {} moved to 'past_due' status due to billing failure", userId);
                
                return ResponseEntity.ok(Map.of(
                    "status", "processed",
                    "message", "User moved to grace period",
                    "user_id", userId,
                    "new_status", "past_due"
                ));
            }
            
            logger.info("ℹ️ User {} already in non-active status: {}", userId, currentStatus);
            return ResponseEntity.ok(Map.of("status", "ignored", "message", "User not in active status"));
            
        } catch (Exception e) {
            logger.error("Error handling billing failure", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process billing failure"));
        }
    }

    private void recordFailedTransaction(Long userId, String platform, String transactionId, String failureReason) {
        try {
            String sql = """
                INSERT INTO payment_transactions 
                (user_id, platform, amount, description, platform_transaction_id, status, transaction_date)
                VALUES (?, ?, ?, ?, ?, 'failed', ?)
            """;
            
            String description = "Billing failed: " + (failureReason != null ? failureReason : "Unknown reason");
            
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            jdbcTemplate.update(sql, userId, platform, 0.00, description, transactionId, now);
            
            logger.info("💳 Recorded failed transaction for user {}: {}", userId, description);
            
        } catch (Exception e) {
            logger.error("❌ Failed to record failed transaction for user {}", userId, e);
        }
    }

    private void recordCancellationTransaction(Long userId) {
        try {
            // Get user's current platform for the cancellation record
            String userSql = "SELECT platform, platform_transaction_id FROM app_user WHERE id = ?";
            Map<String, Object> userInfo = jdbcTemplate.queryForMap(userSql, userId);
            
            String platform = (String) userInfo.get("platform");
            // Generate unique cancellation transaction ID (don't reuse original purchase token)
            String cancellationTransactionId = platform.toLowerCase() + "_cancel_" + UUID.randomUUID().toString().substring(0, 8);
            
            String sql = """
                INSERT INTO payment_transactions 
                (user_id, platform, amount, description, platform_transaction_id, status, transaction_date)
                VALUES (?, ?, ?, ?, ?, 'cancelled', ?)
            """;
            
            String description = "Subscription cancelled by user";
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            
            jdbcTemplate.update(sql, userId, platform, 0.00, description, cancellationTransactionId, now);
            
            logger.info("🚫 Recorded cancellation transaction for user {}", userId);
            
        } catch (Exception e) {
            logger.error("❌ Failed to record cancellation transaction for user {}", userId, e);
            // Don't fail the cancellation if transaction recording fails
        }
    }

    private void updateUserSubscriptionStatus(Long userId, String newStatus) {
        try {
            String sql = "UPDATE app_user SET subscription_status = ?, updated_at = ? WHERE id = ?";
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            jdbcTemplate.update(sql, newStatus, now, userId);
            logger.info("✅ Updated user {} subscription status to: {}", userId, newStatus);
        } catch (Exception e) {
            logger.error("❌ Failed to update subscription status for user {}", userId, e);
        }
    }

    private void sendBillingFailureNotification(Long userId, String fcmToken, String failureReason) {
        try {
            if (fcmToken == null || fcmToken.trim().isEmpty()) {
                logger.debug("📱 No FCM token for user {}, skipping push notification", userId);
                return;
            }
            
            // Here you would integrate with your PushNotificationService
            // For now, just log what we would send
            String title = "Payment Issue - FamilyNest";
            String body = "We couldn't process your payment. Please update your payment method to continue using FamilyNest.";
            
            logger.info("📱 Would send billing failure notification to user {}: {}", userId, body);
            
            // TODO: Integrate with PushNotificationService
            // pushNotificationService.sendNotification(fcmToken, title, body);
            
        } catch (Exception e) {
            logger.error("❌ Failed to send billing failure notification to user {}", userId, e);
        }
    }

    // REAL API IMPLEMENTATIONS (placeholder for when approved)

    private ResponseEntity<Map<String, Object>> verifyWithApple(Long userId, String receiptData, String productId) {
        try {
            logger.info("🍎 Real Apple App Store verification for user {} product {}", userId, productId);
            
            // TODO: Implement real Apple receipt verification
            // For now, simulate the real verification process
            
            // In production, this would call:
            // POST https://buy.itunes.apple.com/verifyReceipt (production)
            // POST https://sandbox.itunes.apple.com/verifyReceipt (sandbox)
            // with receipt data and app-specific shared secret
            
            // For testing purposes, accept any valid-looking receipt
            if (receiptData != null && receiptData.length() > 10) {
                String transactionId = "apple_" + UUID.randomUUID().toString().substring(0, 8);
                
                // Update user subscription in database
                updateUserSubscription(userId, "active", "APPLE", transactionId, productId);
                
                return ResponseEntity.ok(Map.of(
                    "status", "verified",
                    "platform", "APPLE",
                    "transaction_id", transactionId,
                    "product_id", productId,
                    "is_trial", false,
                    "message", "Apple App Store verification successful"
                ));
            } else {
                logger.warn("🍎 Invalid Apple receipt data: {}", receiptData);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid receipt data"));
            }
            
        } catch (Exception e) {
            logger.error("🍎 Apple verification error", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Apple verification failed: " + e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> verifyWithGoogle(Long userId, String purchaseToken, String productId) {
        try {
            logger.info("🤖 Real Google Play verification for user {} product {}", userId, productId);
            
            // TODO: Implement real Google Play Developer API call
            // For now, simulate the real verification process
            
            // In production, this would call:
            // POST https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}
            // with proper OAuth2 authentication
            
            // For testing purposes, accept any valid-looking token
            if (purchaseToken != null && purchaseToken.length() > 10) {
                String transactionId = "goog_" + UUID.randomUUID().toString().substring(0, 8);
                
                // Update user subscription in database
                updateUserSubscription(userId, "active", "GOOGLE", transactionId, productId);
                
                return ResponseEntity.ok(Map.of(
                    "status", "verified",
                    "platform", "GOOGLE", 
                    "transaction_id", transactionId,
                    "product_id", productId,
                    "is_trial", false,
                    "message", "Google Play verification successful"
                ));
            } else {
                logger.warn("🤖 Invalid Google purchase token: {}", purchaseToken);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid purchase token"));
            }
            
        } catch (Exception e) {
            logger.error("🤖 Google verification error", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Google verification failed: " + e.getMessage()));
        }
    }
}
