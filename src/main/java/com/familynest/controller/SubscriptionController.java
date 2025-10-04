package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling subscription-related endpoints.
 * 
 * SUBSCRIPTION MODEL:
 * 1. App initiates purchase through platform store (Google Play/Apple App Store)
 * 2. On successful purchase, app sends purchase details to backend via /put-subscription
 *    - This establishes the user_id ↔ purchase_token link for future webhook notifications
 * 3. Backend stores purchase details and updates user subscription status
 * 4. For subsequent status changes (renewal, cancellation, expiration):
 *    - Google Play: Real-Time Developer Notifications webhook (/google-webhook) updates backend directly
 *    - Apple: Server-to-server notifications will update backend directly (TODO: implement /apple-webhook)
 * 5. App queries backend for current subscription status via /get-subscription
 * 
 * This model minimizes app-side logic and relies on server-side webhooks for accuracy.
 * The backend is the source of truth for subscription status.
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private AuthUtil authUtil;
    
    /**
     * Google Play Real-Time Developer Notifications webhook endpoint
     * This endpoint receives notifications from Google Play about subscription events
     * See: https://developer.android.com/google/play/billing/rtdn-reference
     */
    @PostMapping("/google-webhook")
    public ResponseEntity<Map<String, Object>> googleWebhook(@RequestBody Map<String, Object> notification) {
        logger.info("📱 Received Google Play RTDN notification: {}", notification);
        
        try {
            // Extract the notification data
            // The structure follows Google's RTDN format
            if (notification.containsKey("message")) {
                Map<String, Object> message = (Map<String, Object>) notification.get("message");
                if (message.containsKey("data")) {
                    // The data is base64 encoded
                    String encodedData = (String) message.get("data");
                    String decodedData = new String(java.util.Base64.getDecoder().decode(encodedData));
                    logger.info("📱 Decoded notification data: {}", decodedData);
                    
                    // Parse the JSON data
                    Map<String, Object> data = new org.springframework.boot.json.JacksonJsonParser().parseMap(decodedData);
                    
                    // Process the notification based on its type
                    String notificationType = (String) data.get("notificationType");
                    logger.info("📱 Notification type: {}", notificationType);
                    
                    // Handle subscription notifications
                    if ("SUBSCRIPTION_PURCHASED".equals(notificationType) || 
                        "SUBSCRIPTION_RENEWED".equals(notificationType) ||
                        "SUBSCRIPTION_CANCELED".equals(notificationType) ||
                        "SUBSCRIPTION_EXPIRED".equals(notificationType)) {
                        
                        processSubscriptionNotification(data);
                    }
                }
            }
            
            // Return 200 OK to acknowledge receipt
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            logger.error("❌ Error processing Google Play notification", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Process a subscription notification from Google Play
     */
    private void processSubscriptionNotification(Map<String, Object> data) {
        try {
            // Extract the subscription info
            Map<String, Object> subscriptionNotification = (Map<String, Object>) data.get("subscriptionNotification");
            if (subscriptionNotification == null) {
                logger.warn("⚠️ No subscription notification data found");
                return;
            }
            
            String purchaseToken = (String) subscriptionNotification.get("purchaseToken");
            String subscriptionId = (String) subscriptionNotification.get("subscriptionId");
            Integer notificationType = (Integer) subscriptionNotification.get("notificationType");
            
            logger.info("📱 Processing subscription notification: token={}, id={}, type={}", 
                purchaseToken, subscriptionId, notificationType);
            
            // Find the user associated with this purchase token
            Long userId = findUserByPurchaseToken(purchaseToken);
            if (userId == null) {
                logger.warn("⚠️ No user found for purchase token: {}", purchaseToken);
                return;
            }
            
            // Process based on notification type
            switch (notificationType) {
                case 1: // SUBSCRIPTION_PURCHASED
                case 2: // SUBSCRIPTION_RENEWED
                    // Update user status to active
                    updateUserSubscriptionStatus(userId, "active");
                    // Record transaction
                    recordPaymentTransaction(userId, "GOOGLE", purchaseToken, subscriptionId, 0.0);
                    logger.info("✅ Updated user {} subscription to ACTIVE", userId);
                    break;
                    
                case 3: // SUBSCRIPTION_CANCELED
                case 4: // SUBSCRIPTION_EXPIRED
                    // Update user status to inactive
                    updateUserSubscriptionStatus(userId, "inactive");
                    // Record transaction
                    recordExpirationTransaction(userId, "GOOGLE", purchaseToken, subscriptionId, 0.0);
                    logger.info("⏰ Updated user {} subscription to INACTIVE", userId);
                    break;
                    
                default:
                    logger.warn("⚠️ Unknown notification type: {}", notificationType);
            }
        } catch (Exception e) {
            logger.error("❌ Error processing subscription notification", e);
        }
    }
    
    /**
     * Find the user ID associated with a purchase token
     */
    /**
     * Apple App Store Server Notifications webhook endpoint.
     * TODO: Implement this endpoint for Apple subscription status updates.
     * 
     * This will receive server-to-server notifications from Apple about subscription events
     * such as renewals, expirations, and cancellations.
     * 
     * See: https://developer.apple.com/documentation/appstoreservernotifications
     */
    @PostMapping("/apple-webhook")
    public ResponseEntity<Map<String, Object>> appleWebhook(@RequestBody Map<String, Object> notification) {
        logger.info("🍎 Received Apple App Store notification: {}", notification);
        // TODO: Implement Apple App Store server notifications processing
        // This will be similar to the Google webhook but with Apple-specific fields
        return ResponseEntity.ok(Map.of("status", "received"));
    }
    
    /**
     * Find a user by their purchase token.
     * This is used to link webhook notifications to the correct user.
     */
    private Long findUserByPurchaseToken(String purchaseToken) {
        try {
            String sql = "SELECT user_id FROM payment_transactions WHERE platform_transaction_id = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, Long.class, purchaseToken);
        } catch (Exception e) {
            logger.warn("⚠️ Could not find user for purchase token: {}", purchaseToken);
            return null;
        }
    }

    /**
     * Record subscription purchase from any platform
     * This endpoint updates the user's subscription status and records the transaction
     */
    @PostMapping("/put-subscription")
    @Transactional
    public ResponseEntity<Map<String, Object>> putSubscription(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {
        
        logger.info("📱 Subscription update request received");
        
        try {
            // Extract user ID from auth token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            
            // Extract request parameters
            String transactionId = (String) request.get("transaction_id");
            String platform = (String) request.get("platform");
            String productId = (String) request.get("product_id");
            Double price = request.get("price") instanceof Number ? 
                ((Number) request.get("price")).doubleValue() : 0.0;
            Boolean isExpired = Boolean.TRUE.equals(request.get("is_expired"));
            
            // Validate required parameters
            if (transactionId == null || platform == null || productId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required parameters: transaction_id, platform, and product_id are required"
                ));
            }
            
            // Validate platform value
            if (!platform.equals("GOOGLE") && !platform.equals("APPLE")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid platform. Must be either 'GOOGLE' or 'APPLE'"
                ));
            }

            if (isExpired) {
                // Handle expired/cancelled subscription
                logger.info("⏰ Recording subscription expiration for user {} product {}", userId, productId);
                
                // 1. Update user subscription status to inactive
                updateUserSubscriptionStatus(userId, "inactive");
                
                // 2. Record expiration in payment_transactions table
                recordExpirationTransaction(userId, platform, transactionId, productId, price);
                
                return ResponseEntity.ok(Map.of(
                    "status", "expired",
                    "user_id", userId,
                    "platform", platform,
                    "transaction_id", transactionId,
                    "message", "Subscription marked as inactive"
                ));
            } else {
                // Record new purchase
                logger.info("💰 Recording {} purchase for user {} product {} price {}", platform, userId, productId, price);
                
                // 1. Update user subscription in app_user table
                updateUserSubscription(userId, platform, transactionId, productId);
                
                // 2. Record transaction in payment_transactions table
                recordPaymentTransaction(userId, platform, transactionId, productId, price);
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "recorded",
                "user_id", userId,
                "platform", platform,
                "transaction_id", transactionId,
                "product_id", productId
            ));

        } catch (Exception e) {
            logger.error("Error recording subscription", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to record subscription: " + e.getMessage()));
        }
    }

    /**
     * Get user's current subscription status
     */
    @GetMapping("/get-subscription")
    public ResponseEntity<Map<String, Object>> getSubscription(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            
            String sql = """
                SELECT subscription_status, trial_end_date, subscription_end_date, 
                       platform, platform_transaction_id, subscription_start_date
                FROM app_user WHERE id = ?
            """;
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);
            
            // Return the raw subscription data - let the client decide what to do with it
            Map<String, Object> response = new HashMap<>(result);
            
            // No hardcoded pricing - the frontend will get real pricing from the app stores
            response.put("monthly_price", 0.00);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting subscription data", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get subscription data"));
        }
    }
    
    /**
     * Get user's payment transaction history
     */
    @GetMapping("/get-payment-history")
    public ResponseEntity<Map<String, Object>> getPaymentHistory(
            @RequestHeader("Authorization") String authHeader) {
        
        logger.info("📊 Payment history request received");
        
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            logger.info("📊 Getting payment history for user ID: {}", userId);
            
            String sql = """
                SELECT id, user_id, platform, amount, currency, transaction_date, 
                       description, platform_transaction_id, product_id, status, 
                       created_at
                FROM payment_transactions 
                WHERE user_id = ? 
                ORDER BY transaction_date DESC
                LIMIT 20
            """;
            
            List<Map<String, Object>> transactions = jdbcTemplate.queryForList(sql, userId);
            logger.info("📊 Found {} transactions for user {}", transactions.size(), userId);
            
            // Format the transaction data for the frontend
            List<Map<String, Object>> formattedTransactions = new ArrayList<>();
            
            for (Map<String, Object> transaction : transactions) {
                Map<String, Object> formatted = new HashMap<>();
                formatted.put("transaction_date", ((java.sql.Timestamp) transaction.get("transaction_date")).getTime());
                formatted.put("amount", transaction.get("amount"));
                formatted.put("description", transaction.get("description"));
                formatted.put("status", transaction.get("status"));
                formatted.put("platform", transaction.get("platform"));
                formatted.put("product_id", transaction.get("product_id"));
                formatted.put("platform_transaction_id", transaction.get("platform_transaction_id"));
                formatted.put("created_at", ((java.sql.Timestamp) transaction.get("created_at")).getTime());
                
                formattedTransactions.add(formatted);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("transactions", formattedTransactions);
            
            logger.info("📊 Retrieved {} payment transactions for user {}", transactions.size(), userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Error getting payment history", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get payment history"));
        }
    }

    private void updateUserSubscription(Long userId, String platform, String transactionId, String productId) {
        // CRITICAL: Use UTC for all subscription time handling
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        
        String sql = """
            UPDATE app_user 
            SET subscription_status = 'active', 
                platform = ?, 
                platform_transaction_id = ?,
                trial_end_date = NULL,
                subscription_start_date = ?,
                subscription_end_date = ?,
                updated_at = ?
            WHERE id = ?
        """;
        
        // Set subscription end date to 30 days from now (for monthly subscription)
        LocalDateTime subscriptionEnd = now.plusDays(30);
        
        jdbcTemplate.update(sql, platform, transactionId, now, subscriptionEnd, now, userId);
        
        logger.info("✅ Updated user {} subscription: platform={}, transaction_id={}", 
                   userId, platform, transactionId);
    }
    
    /**
     * Record a payment transaction in the history
     * Only adds a new record if the status has changed from the most recent record
     */
    private void recordPaymentTransaction(Long userId, String platform, String transactionId, String productId, double price) {
        try {
            // Check the most recent transaction for this user and product
            String checkSql = """
                SELECT platform_transaction_id, status 
                FROM payment_transactions 
                WHERE user_id = ? 
                AND product_id = ?
                ORDER BY transaction_date DESC
                LIMIT 1
            """;
            
            boolean shouldInsert = true;
            
            try {
                // Query for the most recent record
                Map<String, Object> lastRecord = jdbcTemplate.queryForMap(checkSql, userId, productId);
                
                // Extract values from the last record
                String lastTransactionId = (String) lastRecord.get("platform_transaction_id");
                String lastStatus = (String) lastRecord.get("status");
                
                // If the last record has the same transaction ID and status, skip creating a duplicate
                if (lastTransactionId != null && lastTransactionId.equals(transactionId) && 
                    lastStatus != null && lastStatus.equals("completed")) {
                    logger.info("⏰ Skipping duplicate completed record for user {}: {} (same as last record)", 
                        userId, productId);
                    shouldInsert = false; // Skip creating a duplicate record
                } else {
                    logger.info("✅ Creating new completed record because it's different from the last record");
                    logger.info("   Last record: transactionId={}, status={}", lastTransactionId, lastStatus);
                    logger.info("   New record:  transactionId={}, status=completed", transactionId);
                }
                
            } catch (Exception e) {
                // If there's no previous record or other error, proceed with creating the record
                logger.info("ℹ️ No previous record found or error occurred, creating first completed record");
            }
            
            if (shouldInsert) {
                // Insert the new record
                String sql = """
                    INSERT INTO payment_transactions 
                    (user_id, platform, amount, description, platform_transaction_id, product_id, status, transaction_date)
                    VALUES (?, ?, ?, ?, ?, ?, 'completed', ?)
                """;
                
                // Use the price from the app store
                double amount = price;
                String description = "Premium subscription - " + productId;
                
                LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
                jdbcTemplate.update(sql, userId, platform, amount, description, transactionId, productId, now);
                
                logger.info("💰 Recorded payment transaction for user {}: {} - {}", userId, platform, productId);
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to record payment transaction for user {}", userId, e);
            // Don't fail the whole subscription process if transaction recording fails
        }
    }
    
    /**
     * Update user's subscription status
     */
    private void updateUserSubscriptionStatus(Long userId, String status) {
        try {
            String sql = "UPDATE app_user SET subscription_status = ?, updated_at = ? WHERE id = ?";
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
            jdbcTemplate.update(sql, status, now, userId);
            logger.info("✅ Updated user {} subscription status to: {}", userId, status);
        } catch (Exception e) {
            logger.error("❌ Failed to update subscription status for user {}", userId, e);
        }
    }
    
    /**
     * Record an expiration/cancellation transaction in the history
     * Only adds a new record if the status has changed from the most recent record
     */
    private void recordExpirationTransaction(Long userId, String platform, String transactionId, String productId, double price) {
        try {
            // Check the most recent transaction for this user and product
            String checkSql = """
                SELECT platform_transaction_id, status 
                FROM payment_transactions 
                WHERE user_id = ? 
                AND product_id = ?
                ORDER BY transaction_date DESC
                LIMIT 1
            """;
            
            boolean shouldInsert = true;
            
            try {
                // Query for the most recent record
                Map<String, Object> lastRecord = jdbcTemplate.queryForMap(checkSql, userId, productId);
                
                // Extract values from the last record
                String lastTransactionId = (String) lastRecord.get("platform_transaction_id");
                String lastStatus = (String) lastRecord.get("status");
                
                // If the last record has the same transaction ID and status, skip creating a duplicate
                if (lastTransactionId != null && lastTransactionId.equals(transactionId) && 
                    lastStatus != null && lastStatus.equals("inactive")) {
                    logger.info("⏰ Skipping duplicate inactive record for user {}: {} (same as last record)", 
                        userId, productId);
                    shouldInsert = false; // Skip creating a duplicate record
                } else {
                    logger.info("✅ Creating new inactive record because it's different from the last record");
                    logger.info("   Last record: transactionId={}, status={}", lastTransactionId, lastStatus);
                    logger.info("   New record:  transactionId={}, status=inactive", transactionId);
                }
                
            } catch (Exception e) {
                // If there's no previous record or other error, proceed with creating the record
                logger.info("ℹ️ No previous record found or error occurred, creating first inactive record");
            }
            
            if (shouldInsert) {
                // Insert the new record
                String sql = """
                    INSERT INTO payment_transactions 
                    (user_id, platform, amount, description, platform_transaction_id, product_id, status, transaction_date)
                    VALUES (?, ?, ?, ?, ?, ?, 'inactive', ?)
                """;
                
                String description = "Subscription inactive - " + productId;
                
                LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
                jdbcTemplate.update(sql, userId, platform, price, description, transactionId, productId, now);
                
                logger.info("⏰ Recorded subscription inactive transaction for user {}: {}", userId, productId);
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to record subscription inactive transaction for user {}", userId, e);
        }
    }
}