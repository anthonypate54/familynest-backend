package com.familynest.controller;

import com.familynest.auth.AuthUtil;
import com.familynest.model.SubscriptionPurchaseModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.json.JacksonJsonParser;
// EmptyResultDataAccessException is not used
// import org.springframework.dao.EmptyResultDataAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// Google Play API imports
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
// import com.google.api.services.androidpublisher.model.Money; // No longer needed
// import com.google.api.services.androidpublisher.model.PricingPhase;

// import com.google.api.services.androidpublisher.model.OfferDetails;
// // import com.google.api.services.androidpublisher.model.Money; // No longer needed

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
public class GoogleSubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleSubscriptionController.class);

    // Google Play API constants
    private static final String PACKAGE_NAME = "com.anthony.familynest";


    // Google Play offer IDs
    private static final String OFFER_ID_30_DAY_FREE_TRIAL = "30-dayfreetrial";

    private final AndroidPublisher androidPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final AuthUtil authUtil;

    /**
     * Constructor with dependency injection
     * The AndroidPublisher bean is created in GooglePlayConfig
     */
    @Autowired
    public GoogleSubscriptionController(
            AndroidPublisher androidPublisher,
            JdbcTemplate jdbcTemplate,
            AuthUtil authUtil) {
        this.androidPublisher = androidPublisher;
        this.jdbcTemplate = jdbcTemplate;
        this.authUtil = authUtil;

        logger.info("✅ GoogleSubscriptionController initialized with Google Play API client");
    }


    /**
     * Record subscription purchase from any platform
     * This endpoint updates the user's subscription status and records the transaction
     */
    @PostMapping("/verify-purchase")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyPurchase(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {

        logger.info("📱 Purchase verification request received");

        try {
            // Extract user ID from auth token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);

            // Extract only essential parameters
            String purchaseToken = (String) request.get("transaction_id");
            String platform = (String) request.get("platform");
            String productId = (String) request.get("product_id");

            // Validate required parameters
            if (purchaseToken == null || platform == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required parameters: transaction_id and platform are required"
                ));
            }

            // Validate platform value
            if (!platform.equals("GOOGLE") && !platform.equals("APPLE")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid platform. Must be either 'GOOGLE' or 'APPLE'"
                ));
            }

            // For Google Play purchases, verify with Google Play API
            boolean isTrial = false;
            double price = 0.0;
            SubscriptionPurchaseModel purchaseModel = null;

            // Validate platform is GOOGLE (this is GoogleSubscriptionController)
            if (!platform.equals("GOOGLE")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid platform for this controller. Must be 'GOOGLE'."
                ));
            }

            // Normal verification flow for real tokens
            if (androidPublisher == null) {
                logger.warn("⚠️ Google Play API client not initialized");
                // Return error to client - server configuration issue
                return ResponseEntity.status(500).body(Map.of(
                    "error", "Server is not properly configured for Google Play verification.",
                    "details", "Google Play API client not initialized"
                ));
            }

            try {
                logger.info("🔍 Verifying Google Play purchase token: {}",
                    purchaseToken.substring(0, Math.min(20, purchaseToken.length())) + "...");

                // Use our improved queryGoogleApiForPurchase method
                purchaseModel = queryGoogleApiForPurchase(purchaseToken);

                // Note: purchaseModel will never be null here because queryGoogleApiForPurchase
                // throws an exception if it fails, which is caught in the outer catch block

                // Get key information from the purchase model
                isTrial = purchaseModel.isTrial();
                price = purchaseModel.getPrice();
                String linkedPurchaseToken = purchaseModel.getLinkedPurchaseToken();
                String subscriptionState = purchaseModel.getSubscriptionState();

                // For verify-purchase, we'll use the current time as the event time
                // This is a client-initiated verification, not a server notification
                // Use notification type 1 (SUBSCRIPTION_RECOVERED) for client-initiated verifications
                  // Log the complete model with notification details
                logger.info("✅ Complete purchase model with notification details: {}", purchaseModel);

                // Log the price information
                logger.info("💰 Subscription price: {}", price);

                // Log linked purchase token if available
                if (linkedPurchaseToken != null && !linkedPurchaseToken.isEmpty()) {
                    logger.info("🔗 Found linked purchase token: {}", linkedPurchaseToken);
                }

                // Log trial status
                logger.info("📅 Trial: {}", isTrial);

                // Log additional subscription details
                logger.info("💳 Subscription details: autoRenewing={}, state={}",
                    purchaseModel.isAutoRenewing(), subscriptionState);

                logger.info("✅ Google Play verification successful: isTrial={}", isTrial);
            } catch (Exception e) {
                logger.error("❌ Failed to verify with Google Play API: {}", e.getMessage());
                // Return error to client - don't proceed with unverified purchases
                return ResponseEntity.status(503).body(Map.of(
                    "error", "Failed to verify purchase with Google Play. Please try again later.",
                    "details", e.getMessage()
                ));
            }

            // The purchaseModel already has all the information we need
            // No need to extract dates separately - we'll use the model directly

            // Log trial dates if this is a trial
            if (isTrial) {
                if (purchaseModel.getTrialStartDate() != null) {
                    logger.info("📅 Trial start date: {}", purchaseModel.getTrialStartDate());
                }

                if (purchaseModel.getTrialEndDate() != null) {
                    logger.info("📅 Trial end date: {}", purchaseModel.getTrialEndDate());
                }
            }

            // Update user subscription
            updateUserSubscription(userId, platform, purchaseToken, purchaseModel);

            // Do NOT record a transaction here - let the RTDN webhook handle that
            // This prevents duplicate transactions
            // Just log that we've verified and updated the user's subscription
            logger.info("✅ Verified {} purchase for user {}: isTrial={} (no transaction recorded - waiting for RTDN)",
                platform, userId, isTrial);

            // Process queued notifications
            try {
                logger.info("🔄 Attempting to process queued notifications for userId={}, purchaseToken={}",
                    userId, purchaseToken);
                // Check for any queued notifications for this purchase token
                processQueuedNotifications(userId, purchaseToken);
            } catch (Exception e) {
                // Log the error but don't fail the whole request
                logger.error("❌ Error processing queued notifications: {}", e.getMessage(), e);
            }

            // Return response with additional helpful information
            Map<String, Object> response = new HashMap<>();
            response.put("status", "recorded");
            response.put("user_id", userId);
            response.put("platform", platform);
            response.put("transaction_id", purchaseToken);
            response.put("product_id", productId);
            response.put("is_trial", isTrial);

            // Include subscription state and auto-renewing status
            response.put("subscription_state", purchaseModel.getSubscriptionState());
            response.put("auto_renewing", purchaseModel.isAutoRenewing());

            // Include subscription end date for all subscriptions (next payment date for auto-renewing)
            if (purchaseModel.getSubscriptionEndDate() != null) {
                // Format date as ISO 8601 string (2023-01-01T00:00:00Z)
                String subscriptionEndDate = purchaseModel.getSubscriptionEndDate()
                    .atZone(ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_INSTANT);

                response.put("subscription_end_date", subscriptionEndDate);

                // For auto-renewing subscriptions, this is the next payment date
                if (purchaseModel.isAutoRenewing()) {
                    response.put("next_payment_date", subscriptionEndDate);
                }
            }

            // Include trial-specific information if this is a trial
            if (isTrial) {
                response.put("is_trial", true);

                // Add trial start date if available
                if (purchaseModel.getTrialStartDate() != null) {
                    // Format date as ISO 8601 string
                    String trialStartDate = purchaseModel.getTrialStartDate()
                        .atZone(ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT);

                    response.put("trial_start_date", trialStartDate);
                }

                // Add trial end date if available
                if (purchaseModel.getTrialEndDate() != null) {
                    // Format date as ISO 8601 string
                    String trialEndDate = purchaseModel.getTrialEndDate()
                        .atZone(ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT);

                    response.put("trial_end_date", trialEndDate);
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error recording subscription", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to record subscription: " + e.getMessage()));
        }
    }


    /**
     * Google Play Real-Time Developer Notifications webhook endpoint
     * This endpoint receives notifications from Google Play about subscription events
     * See: https://developer.android.com/google/play/billing/rtdn-reference
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/google-webhook")
    public ResponseEntity<String> googleWebhook(@RequestBody Map<String, Object> body) {
        logger.info("Received Google Play RTDN notification");

        try {
            // Log the raw body for debugging
            try {
                String rawBody = new ObjectMapper().writeValueAsString(body);
                logger.info("📩 Raw webhook body: {}", rawBody);
            } catch (Exception e) {
                logger.warn("Could not serialize webhook body for logging", e);
            }

            Map<String, Object> subscriptionNotification;
            String purchaseToken;
            Integer notificationType;

            // The Pub/Sub message is wrapped in a POST request body
            String pubsubMessageStr = (String) ((Map<String, Object>) body.get("message")).get("data");

            // Log message ID for duplicate detection
            String messageId = (String) ((Map<String, Object>) body.get("message")).get("messageId");
            logger.info("📩 Processing Pub/Sub message ID: {}", messageId);

            // Decode the base64 encoded message data
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(pubsubMessageStr);
            String messageData = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Parse the JSON message using JacksonJsonParser
            Map<String, Object> notification = new JacksonJsonParser().parseMap(messageData);
            logger.info("Received RTDN: " + messageData);

            // Extract the eventTimeMillis from the notification
            // We'll access this through the _parentNotification reference in processRTDNNotification

            // Check if this is a test notification
            if (notification.containsKey("testNotification")) {
                logger.info("Received test notification - skipping processing");
                return ResponseEntity.ok("Test notification received");
            }

            subscriptionNotification = (Map<String, Object>) notification.get("subscriptionNotification");
            if (subscriptionNotification == null) {
                logger.error("No subscriptionNotification found in notification");
                return ResponseEntity.badRequest().body("Invalid notification format");
            }

            purchaseToken = (String) subscriptionNotification.get("purchaseToken");
            notificationType = ((Number) subscriptionNotification.get("notificationType")).intValue();

            // No need to extract or add eventTimeMillis separately anymore
            // We'll pass the full notification to processRTDNNotification


            // Common processing logic for both test and production
            logger.info("Processing notification: type={}, token={}", notificationType, purchaseToken);

            // Try to find the user associated with this purchase token
            Long userId = findUserByPurchaseToken(purchaseToken);

            if (userId != null) {
                // User found, process the notification immediately
                logger.info("Found user {} for purchase token, processing notification", userId);
                processRTDNNotification(userId, notification);
            } else {
                // User not found, store in queue for later processing
                logger.info("No user found for token: {}. Adding to queue for later processing.", purchaseToken);

                // Store the full notification in the queue
                String notificationJson = new ObjectMapper().writeValueAsString(notification);
                String sql = "INSERT INTO rtdn_notification_queue " +
                           "(purchase_token, notification_data, notification_type) VALUES (?, ?::jsonb, ?)";

                jdbcTemplate.update(sql, purchaseToken, notificationJson, notificationType);
            }

            // Always return 200 OK
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            logger.error("Error processing Google Play notification", e);
            // Still return 200 OK to prevent Google from retrying
            return ResponseEntity.ok("Error handled");
        }
    }

    // Apple webhook endpoint removed - will be implemented in AppleSubscriptionController

    /**
     * Find a user by their purchase token.
     * This is used to link webhook notifications to the correct user.
     */
    /**
     * Process a Google Play RTDN (Real-Time Developer Notification) for a specific user
     * This is the shared method used by both the webhook handler and the queue processor
     */
    private void processRTDNNotification(Long userId, Map<String, Object> notification) {
        // Extract the subscription notification part - this is the standard Google RTDN structure
        @SuppressWarnings("unchecked")
        Map<String, Object> subscriptionNotification = (Map<String, Object>) notification.get("subscriptionNotification");

        if (subscriptionNotification == null) {
            throw new IllegalArgumentException("subscriptionNotification is missing in the notification");
        }

        // Extract key fields directly from the standard Google structure
        String purchaseToken = (String) subscriptionNotification.get("purchaseToken");
        if (purchaseToken == null) {
            throw new IllegalArgumentException("purchaseToken is missing in the notification");
        }

        // Extract notificationType
        Number notificationTypeNum = (Number) subscriptionNotification.get("notificationType");
        if (notificationTypeNum == null) {
            throw new IllegalArgumentException("notificationType is missing in the notification");
        }
        Integer notificationType = notificationTypeNum.intValue();

        logger.info("🔄 Processing notification type {} for user {} with token {}",
                   notificationType, userId, purchaseToken);

        // Extract eventTimeMillis - defined as a long in Google's documentation
        // But might come as a String in queued notifications due to JSON serialization
        Long eventTimeMillis;
        Object rawValue = notification.get("eventTimeMillis");
        if (rawValue == null) {
            throw new IllegalArgumentException("eventTimeMillis is missing in the notification");
        }

        if (rawValue instanceof Number) {
            eventTimeMillis = ((Number) rawValue).longValue();
        } else if (rawValue instanceof String) {
            // Handle case where eventTimeMillis comes as a String (from JSON serialization)
            try {
                eventTimeMillis = Long.parseLong((String) rawValue);
                logger.info("📅 Converted eventTimeMillis from String to Long: {}", eventTimeMillis);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("eventTimeMillis string could not be parsed as a number: " + rawValue);
            }
        } else {
            throw new IllegalArgumentException("eventTimeMillis must be a number or string, received: " + rawValue.getClass().getName());
        }
        logger.info("📅 Extracted eventTimeMillis from notification: {}", eventTimeMillis);

        // Query Google API for purchase details - do this ONCE before the switch
        SubscriptionPurchaseModel purchase = queryGoogleApiForPurchase(purchaseToken);
        if (purchase == null) {
            throw new IllegalArgumentException("purchase can not be null");
        }

        // Log the purchase model from Google API
        logger.info("✅ Retrieved purchase model from Google API: {}", purchase);

        // Process based on notification type
        switch (notificationType) {
            case 4: // SUBSCRIPTION_PURCHASED
                // Record transaction with notification type and event time from the webhook
                recordPaymentTransaction(userId, purchase, notificationType, eventTimeMillis);
                logger.info("✅ Updated user {} subscription (new purchase)", userId);
                break;

            case 2: // SUBSCRIPTION_RENEWED
                // Record transaction with notification type and event time from the webhook
                recordPaymentTransaction(userId, purchase, notificationType, eventTimeMillis);
                logger.info("✅ Updated user {} subscription (renewed)", userId);
                break;

            case 3: // SUBSCRIPTION_CANCELED
                // Record transaction with notification type and event time from the webhook
                recordPaymentTransaction(userId, purchase, notificationType, eventTimeMillis);
                logger.info("⏰ Updated user {} subscription (canceled)", userId);
                break;

            case 13: // SUBSCRIPTION_EXPIRED
                // Record transaction with notification type and event time from the webhook
                recordPaymentTransaction(userId, purchase, notificationType, eventTimeMillis);
                logger.info("⏰ Updated user {} subscription (expired)", userId);
                break;

            default:
                logger.warn("⚠️ Unknown notification type: {}", notificationType);
        }
    }

    /**
     * Process any queued notifications for a purchase token
     * This is called after a purchase is verified and the user-token association is established
     * Uses a new transaction to ensure queue entries are deleted even if processing fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void processQueuedNotifications(Long userId, String purchaseToken) {
        logger.info("🔍 Checking for queued notifications for userId={}, purchaseToken={}", userId, purchaseToken);
        try {
            // Check for queued notifications for this purchase token
            String sql = "SELECT id, notification_data, notification_type FROM rtdn_notification_queue " +
                         "WHERE purchase_token = ? AND processed = FALSE ORDER BY received_at ASC";

            logger.info("🔍 Executing SQL: {}", sql);
            List<Map<String, Object>> queuedNotifications = jdbcTemplate.queryForList(sql, purchaseToken);
            logger.info("🔍 Found {} queued notifications", queuedNotifications.size());

            if (!queuedNotifications.isEmpty()) {
                logger.info("🔄 Found {} queued notifications for purchase token: {}",
                    queuedNotifications.size(), purchaseToken);

                for (Map<String, Object> queueItem : queuedNotifications) {
                    // Use safe conversion methods instead of direct casting
                    Long queueId = ((Number) queueItem.get("id")).longValue();
                    String notificationJson = queueItem.get("notification_data").toString();
                    Integer notificationType = ((Number) queueItem.get("notification_type")).intValue();

                    logger.info("🔄 Processing queued notification ID: {}, type: {}", queueId, notificationType);

                    try {
                        // Parse the notification
                        ObjectMapper mapper = new ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> notificationData = mapper.readValue(notificationJson, Map.class);

                        // The notification_data column contains the complete notification as it was received
                        // from Google, so we should use it directly without wrapping it
                        Map<String, Object> fullNotification = notificationData;

                        logger.info("🔄 Reconstructed full notification from queue: {}", fullNotification);

                        // Process the notification
                        processRTDNNotification(userId, fullNotification);

                        logger.info("✅ Successfully processed queued notification ID: {}", queueId);
                    } catch (Exception e) {
                        // Log the error but continue to delete the notification
                        logger.error("❌ Error processing queued notification {}: {}", queueId, e.getMessage(), e);
                    }

                    // Always delete the notification regardless of success or failure
                    // This prevents endless retry loops for notifications that can't be processed
                    try {
                        logger.info("🔄 Deleting notification ID: {} from queue", queueId);

                        int updateCount = jdbcTemplate.update(
                            "DELETE FROM rtdn_notification_queue WHERE id = ?",
                            queueId
                        );
                        logger.info("🔄 Delete result: {} row(s) affected", updateCount);
                    } catch (Exception e) {
                        logger.error("❌ Failed to delete notification {}: {}", queueId, e.getMessage(), e);
                    }
                }
            } else {
                logger.info("ℹ️ No queued notifications found for purchase token: {}", purchaseToken);
            }
        } catch (Exception e) {
            logger.error("❌ Error checking for queued notifications: {}", e.getMessage(), e);
        }
    }

    /**
     * Process all queued notifications
     * This scheduled task runs every 15 minutes to process any queued notifications
     * that haven't been processed yet due to race conditions
     */
    @Scheduled(fixedRate = 900000) // Run every 15 minutes (900,000 ms)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAllQueuedNotifications() {
        logger.info("🔄 Scheduled task: Processing all queued notifications");

        try {
            // Get all unprocessed notifications
            String sql = "SELECT id, purchase_token, notification_data, notification_type FROM rtdn_notification_queue " +
                         "WHERE processed = FALSE ORDER BY received_at ASC";

            List<Map<String, Object>> queuedNotifications = jdbcTemplate.queryForList(sql);
            logger.info("🔍 Found {} queued notifications", queuedNotifications.size());

            if (queuedNotifications.isEmpty()) {
                logger.info("✅ No queued notifications to process");
                return;
            }

            int processedCount = 0;
            int failedCount = 0;

            for (Map<String, Object> queueItem : queuedNotifications) {
                Long queueId = ((Number) queueItem.get("id")).longValue();
                String purchaseToken = (String) queueItem.get("purchase_token");
                String notificationJson = queueItem.get("notification_data").toString();
                Integer notificationType = ((Number) queueItem.get("notification_type")).intValue();

                logger.info("🔄 Processing queued notification ID: {}, token: {}, type: {}",
                    queueId, purchaseToken, notificationType);

                try {
                    // Find user for this purchase token
                    Long userId = findUserByPurchaseToken(purchaseToken);

                    if (userId != null) {
                        // Parse the notification
                        ObjectMapper mapper = new ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> notificationData = mapper.readValue(notificationJson, Map.class);

                        // Process the notification
                        processRTDNNotification(userId, notificationData);
                        processedCount++;
                        logger.info("✅ Successfully processed notification for user {}", userId);
                    } else {
                        logger.warn("⚠️ No user found for purchase token: {}", purchaseToken);
                        failedCount++;
                    }
                } catch (Exception e) {
                    logger.error("❌ Error processing notification {}: {}", queueId, e.getMessage(), e);
                    failedCount++;
                } finally {
                    // Always delete the notification regardless of success or failure
                    try {
                        logger.info("🔄 Deleting notification ID: {} from queue", queueId);

                        int updateCount = jdbcTemplate.update(
                            "DELETE FROM rtdn_notification_queue WHERE id = ?",
                            queueId
                        );
                        logger.info("🔄 Delete result: {} row(s) affected", updateCount);
                    } catch (Exception e) {
                        logger.error("❌ Failed to delete notification {}: {}", queueId, e.getMessage(), e);
                    }
                }
            }

            logger.info("✅ Scheduled task completed: processed={}, failed={}", processedCount, failedCount);
        } catch (Exception e) {
            logger.error("❌ Error in scheduled task: {}", e.getMessage(), e);
        }
    }

    /**
     * Find the user ID associated with a purchase token
     * Checks both the platform_transaction_id and linked_purchase_token columns
     *
     * @param purchaseToken The purchase token to look up
     * @return The user ID, or null if not found
     */
    private Long findUserByPurchaseToken(String purchaseToken) {
        try {
            // First try to find by platform_transaction_id (primary token)
            String sql = "SELECT user_id FROM payment_transactions WHERE platform_transaction_id = ? LIMIT 1";
            try {
                Long userId = jdbcTemplate.queryForObject(sql, Long.class, purchaseToken);
                if (userId != null) {
                    logger.info("✅ Found user {} for purchase token (primary match)", userId);
                    return userId;
                }
            } catch (Exception e) {
                // No match on primary token, continue to linked token check
                logger.debug("No match on primary token, checking linked tokens");
            }

            // Then try to find by linked_purchase_token
            String linkedSql = "SELECT user_id FROM payment_transactions WHERE linked_purchase_token = ? LIMIT 1";
            try {
                Long userId = jdbcTemplate.queryForObject(linkedSql, Long.class, purchaseToken);
                if (userId != null) {
                    logger.info("✅ Found user {} for purchase token (linked match)", userId);
                    return userId;
                }
            } catch (Exception e) {
                // No match on linked token either
                logger.debug("No match on linked token either");
            }

            // No match found
            logger.warn("⚠️ Could not find user for purchase token: {}", purchaseToken);
            return null;
        } catch (Exception e) {
            // This is a legitimate case - the user might not exist yet
            // But we should log it clearly
            logger.warn("⚠️ Could not find user for purchase token: {} - {}", purchaseToken, e.getMessage());
            return null;
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

    /**
     * Updates the user's subscription information in the database
     *
     * @param userId The user ID
     * @param platform The platform (GOOGLE or APPLE)
     * @param transactionId The purchase token
     * @param purchase The subscription purchase model containing all subscription details
     */
    private void updateUserSubscription(Long userId, String platform, String transactionId,
                                   SubscriptionPurchaseModel purchase) {
        // Use the provided purchase model - no need to make another API call

        // CRITICAL: Use UTC for all subscription time handling
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

        // Get trial information from the purchase model
        boolean isTrial = purchase.isTrial();
        LocalDateTime trialStartDate = purchase.getTrialStartDate();
        LocalDateTime trialEndDate = purchase.getTrialEndDate();

        if (isTrial) {
            logger.info("📅 Setting trial start date: {}", trialStartDate);
            logger.info("📅 Setting trial end date: {}", trialEndDate);
        }

        String sql = """
            UPDATE app_user
            SET subscription_status = ?,
                platform = ?,
                platform_transaction_id = ?,
                trial_start_date = ?,
                trial_end_date = ?,
                subscription_start_date = ?,
                subscription_end_date = ?,
                updated_at = ?
            WHERE id = ?
        """;

        // Get subscription state from our model
        String status = purchase.getSubscriptionState();

        // Get product ID from the purchase model
        String productId = purchase.getProductId();

        // Log what we're actually updating in the database
        logger.info("💾 Updating user {} subscription: status={}, productId={}, trial_start_date={}, trial_end_date={}, subscription_end_date={}",
            userId, status, productId, trialStartDate, trialEndDate, purchase.getSubscriptionEndDate());

        jdbcTemplate.update(sql, status, platform, transactionId,
            trialStartDate, trialEndDate, now, purchase.getSubscriptionEndDate(), now, userId);

        logger.info("✅ Updated user {} subscription: platform={}, transaction_id={}, status={}",
                   userId, platform, transactionId, status);
    }

    /**
     * Record a payment transaction in the history
     * This is the single point of responsibility for all transaction inserts
     * It's fully idempotent - checks if transaction exists first
     * Queries the Google Play API for complete details about the transaction
     */
    /**
     * Queries Google Play API for subscription purchase details and returns a model object
     *
     * @param transactionId The purchase token to look up
     * @return A SubscriptionPurchaseModel with the subscription details, or null if there was an error
     */
    private SubscriptionPurchaseModel queryGoogleApiForPurchase(String transactionId) {
        try {
            logger.info("🔍 Fetching subscription details for token: {}", transactionId);

            // Create the API request
            AndroidPublisher.Purchases.Subscriptionsv2.Get getRequest =
                androidPublisher.purchases().subscriptionsv2().get(PACKAGE_NAME, transactionId);

            // Execute the request
            SubscriptionPurchaseV2 purchase = getRequest.execute();

            if (purchase == null) {
                logger.warn("⚠️ Google Play API returned null purchase for token: {}", transactionId);
                return null;
            }

            // Log the full purchase object for debugging
            try {
                logger.debug("Full purchase object: {}", new ObjectMapper().writeValueAsString(purchase));
            } catch (Exception e) {
                logger.warn("Could not serialize purchase for logging", e);
            }

            // Create and populate our model with direct access to API data
            SubscriptionPurchaseModel model = new SubscriptionPurchaseModel();
            model.setPurchaseToken(transactionId);
            model.setLinkedPurchaseToken(purchase.getLinkedPurchaseToken());
            model.setSubscriptionState(purchase.getSubscriptionState());

            // Parse subscription start time (not trial start time)
            // The start time is when the subscription began, regardless of trial status
            try {
                // Just validate that we can parse the start time
                Instant.parse(purchase.getStartTime());
                // We'll set the trial start date later if we determine this is a trial
            } catch (Exception e) {
                String errorMsg = "Failed to parse subscription start time: " + purchase.getStartTime();
                logger.error("❌ " + errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            // Get the first line item (should always exist for a valid subscription)
            if (purchase.getLineItems() == null || purchase.getLineItems().isEmpty()) {
                String errorMsg = "No line items found in subscription purchase from Google Play API";
                logger.error("❌ " + errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            // Get the first line item as a SubscriptionPurchaseLineItem
            SubscriptionPurchaseLineItem lineItem = purchase.getLineItems().get(0);

            try {
                // Extract product ID
                String productId = lineItem.getProductId();
                model.setProductId(productId);

                // Extract expiry time
                if (lineItem.getExpiryTime() != null) {
                    try {
                        Instant expiryInstant = Instant.parse(lineItem.getExpiryTime());
                        LocalDateTime expiryDateTime = LocalDateTime.ofInstant(expiryInstant, ZoneOffset.UTC);
                        model.setSubscriptionEndDate(expiryDateTime);
                    } catch (Exception e) {
                        logger.warn("⚠️ Could not parse expiry time: {}", e.getMessage());
                    }
                }

                // Extract offer details - for trials, we expect offer details to be present
                try {
                    // Get offer ID and check for our specific trial offer ID
                    String offerId = lineItem.getOfferDetails().getOfferId();
                    model.setOfferId(offerId);

                    if (OFFER_ID_30_DAY_FREE_TRIAL.equals(offerId)) {
                        model.setTrial(true);
                    }

                    // Also check offer tags for FREE_TRIAL indicator
                    List<String> offerTags = lineItem.getOfferDetails().getOfferTags();
                    if (offerTags != null && offerTags.contains("FREE_TRIAL")) {
                        model.setTrial(true);
                    }
                } catch (NullPointerException e) {
                    // If any part of the offer details is null, this is likely not a trial
                    logger.debug("No offer details found, likely not a trial subscription");
                }

                // Try to extract price information directly from the line item
                try {
                    // First check if we can get price from autoRenewingPlan
                    if (lineItem.getAutoRenewingPlan() != null &&
                        lineItem.getAutoRenewingPlan().getRecurringPrice() != null) {

                        // Get price directly from recurring price
                        // The Money class has getUnits() method that returns the whole units of currency
                        try {
                            // Try direct access first
                            if (lineItem.getAutoRenewingPlan().getRecurringPrice().getUnits() != null) {
                                Long units = lineItem.getAutoRenewingPlan().getRecurringPrice().getUnits();
                                double price = units.doubleValue();
                                model.setPrice(price);

                                // If price is 0, it's likely a trial
                                if (units == 0) {
                                    model.setTrial(true);
                                }
                            } else {
                                throw new IllegalStateException("Could not get price units from Google Play API");
                            }
                        } catch (NoSuchMethodError e) {
                            // If direct access fails, throw an exception - we need to fix the code
                            throw new IllegalStateException("Google Play API has changed - getUnits() method not found", e);
                        }
                    }

                    // Check for trial based on offer details
                    if (lineItem.getOfferDetails() != null) {
                        String offerId = lineItem.getOfferDetails().getOfferId();
                        if (OFFER_ID_30_DAY_FREE_TRIAL.equals(offerId)) {
                            model.setTrial(true);

                            // Calculate trial end date based on expiry time if available
                            if (lineItem.getExpiryTime() != null) {
                                try {
                                    Instant expiryInstant = Instant.parse(lineItem.getExpiryTime());
                                    LocalDateTime expiryDateTime = LocalDateTime.ofInstant(expiryInstant, ZoneOffset.UTC);
                                    model.setTrialEndDate(expiryDateTime);

                                    // Use the actual start time from the purchase object
                                    try {
                                        Instant startInstant = Instant.parse(purchase.getStartTime());
                                        LocalDateTime startDateTime = LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC);
                                        model.setTrialStartDate(startDateTime);
                                    } catch (Exception e) {
                                        logger.error("❌ Failed to parse subscription start time: {}", e.getMessage());
                                        throw new IllegalStateException("Failed to parse subscription start time", e);
                                    }

                                    logger.debug("📅 Trial dates - start: {}, end: {}",
                                                purchase.getStartTime(), lineItem.getExpiryTime());
                                } catch (Exception e) {
                                    logger.warn("⚠️ Could not calculate trial dates from expiry time: {}", e.getMessage());
                                }
                            }
                        }
                    }

                    // If price is still 0 and we haven't determined it's a trial, throw an exception
                    if (model.getPrice() == 0.0 && !model.isTrial()) {
                        throw new IllegalStateException("Could not determine price from Google Play API response");
                    }
                } catch (Exception e) {
                    // Rethrow as IllegalStateException to fail fast
                    throw new IllegalStateException("Failed to extract price information: " + e.getMessage(), e);
                }

                // Check for auto-renewing plan
                model.setAutoRenewing(lineItem.getAutoRenewingPlan() != null);

            } catch (Exception e) {
                logger.error("❌ Error extracting data from line item: {}", e.getMessage());
                throw new IllegalStateException("Failed to extract data from subscription line item: " + e.getMessage());
            }

            logger.info("✅ Successfully fetched subscription details: {}", model);
            return model;

        } catch (Exception e) {
            logger.error("❌ Failed to fetch subscription details: {}", e.getMessage());
            throw new IllegalStateException("Failed to fetch subscription details: " + e.getMessage(), e);
        }
    }

    /**
     * DEPRECATED - DO NOT USE
     *
     * We no longer record transactions from verifyPurchase - only from RTDN webhook.
     * This prevents duplicate transactions and maintains a single source of truth.
     *
     * This method is kept for historical reference only.
     *
     * @param userId The user ID
     * @param purchaseModel The already fetched subscription purchase model
     * @deprecated Use RTDN webhook for recording transactions instead
     */
    @SuppressWarnings("unused")
    @Deprecated
    private void recordVerifyTransaction(Long userId, SubscriptionPurchaseModel purchaseModel, Integer notificationType, Long eventTimeMillis) {
        try {
            // Get all the data we need from the model
            String transactionId = purchaseModel.getPurchaseToken();
            String platform = "GOOGLE"; // This is GoogleSubscriptionController
            String productId = purchaseModel.getProductId();
            double price = purchaseModel.getPrice();
            String linkedPurchaseToken = purchaseModel.getLinkedPurchaseToken();
            String transactionStatus = purchaseModel.getSubscriptionState();

            // Get the current time in UTC
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

            // Check if we've already processed this notification
            if (isNotificationProcessed(transactionId, notificationType, eventTimeMillis)) {
                return;
            }
            else {
                // otherwise insert a new notification
                try {
                    String insertSql = "INSERT INTO rtdn_processed_notifications (platform, purchase_token, notification_type, event_time_millis) VALUES (?, ?, ?, ?)";
                    jdbcTemplate.update(insertSql, platform, transactionId, notificationType, eventTimeMillis);
                    logger.info("📝 Recorded processed notification: token={}, type={}, time={}",
                        transactionId, notificationType, eventTimeMillis);
                } catch (Exception e) {
                    // Log the error but don't throw - this is not critical
                    logger.error("❌ Error recording processed notification: {}", e.getMessage(), e);
                }
            }

            String sql = """
                INSERT INTO payment_transactions
                (user_id, platform, amount, description, platform_transaction_id, linked_purchase_token, product_id, status, transaction_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            jdbcTemplate.update(sql, userId, platform, price, transactionStatus, transactionId, linkedPurchaseToken, productId, transactionStatus, now);

            logger.info("💰 Recorded payment transaction for user {}: {} - {} - price: {}",
                userId, platform, productId, price);
            if (linkedPurchaseToken != null) {
                logger.info("🔗 Linked purchase token recorded: {}", linkedPurchaseToken);
            }
        } catch (Exception e) {
            // Rethrow to fail fast - we need to know if transaction recording fails
            throw new IllegalStateException("Failed to record payment transaction for user " + userId + ": " + e.getMessage(), e);
        }
    }


    /**
     * Check if a notification has already been processed
     *
     * @param transactionId The purchase token
     * @param notificationType The notification type
     * @param eventTimeMillis The event time in milliseconds
     * @return true if this is a duplicate notification, false otherwise
     */
    private boolean isNotificationProcessed(String transactionId, Integer notificationType, Long eventTimeMillis) {
        try {
            // These fields are required for duplicate detection
            if (eventTimeMillis == null || notificationType == null) {
                throw new IllegalArgumentException("eventTimeMillis and notificationType are required for duplicate detection");
            }

            String platform = "GOOGLE"; // This is GoogleSubscriptionController

            // Check if we've already processed this exact notification
            String checkSql = "SELECT COUNT(*) FROM rtdn_processed_notifications WHERE purchase_token = ? AND platform = ? AND event_time_millis = ? AND notification_type = ?";
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, transactionId, platform, eventTimeMillis, notificationType);

            if (count > 0) {
                logger.info("💰 Duplicate notification already processed: token={}, type={}, time={}",
                    transactionId, notificationType, eventTimeMillis);
                return true;
            }

            return false;
        } catch (Exception e) {
            // If there's an error checking for duplicates, log it but continue
            // This is one case where we don't want to fail fast, as it's better to risk
            // duplicate processing than to miss processing a notification
            logger.error("❌ Error checking for duplicate notifications: {}", e.getMessage(), e);
            return false;
        }
    }


    private void recordPaymentTransaction(Long userId, SubscriptionPurchaseModel purchase, Integer notificationType, Long eventTimeMillis) {
        try {
            // Extract all the data we need from the purchase model
            String platform = "GOOGLE"; // This is GoogleSubscriptionController
            String transactionId = purchase.getPurchaseToken();
            String productId = purchase.getProductId();
            double price = purchase.getPrice();
            String linkedPurchaseToken = purchase.getLinkedPurchaseToken();

            // Check if we've already processed this notification
            if (isNotificationProcessed(transactionId, notificationType, eventTimeMillis)) {
                return;
            }
            else {
                // otherwise insert a new notification
                try {
                    String insertSql = "INSERT INTO rtdn_processed_notifications (platform, purchase_token, notification_type, event_time_millis) VALUES (?, ?, ?, ?)";
                    jdbcTemplate.update(insertSql, platform, transactionId, notificationType, eventTimeMillis);
                    logger.info("📝 Recorded processed notification: token={}, type={}, time={}",
                        transactionId, notificationType, eventTimeMillis);
                } catch (Exception e) {
                    // Log the error but don't throw - this is not critical
                    logger.error("❌ Error recording processed notification: {}", e.getMessage(), e);
                }
            }
            logger.info("💰 Retrieved complete details from model: price={}, linkedToken={}",
                price, linkedPurchaseToken != null ? "present" : "null");

            // Get the current time in UTC
            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

            // No duplicate check on payment_transactions - we want to record all state changes
            // This allows us to see the full subscription lifecycle (trial → active → canceled)

            // Insert the new record
            String sql = """
                INSERT INTO payment_transactions
                (user_id, platform, amount, description, platform_transaction_id, linked_purchase_token, product_id, status, transaction_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            // Use what the Google Play API returns directly
            String transactionStatus = purchase.getSubscriptionState();

            // Insert the transaction
            jdbcTemplate.update(sql, userId, platform, price, transactionStatus, transactionId, linkedPurchaseToken, productId, transactionStatus, now);

            logger.info("💰 Recorded payment transaction for user {}: {} - {} - price: {}",
                userId, platform, productId, price);
            if (linkedPurchaseToken != null) {
                logger.info("🔗 Linked purchase token recorded: {}", linkedPurchaseToken);
            }

        } catch (Exception e) {
            // Rethrow to fail fast - we need to know if transaction recording fails
            throw new IllegalStateException("Failed to record payment transaction for user " + userId + ": " + e.getMessage(), e);
        }
    }
}
