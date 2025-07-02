package com.familynest.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;

import java.util.Map;
import java.util.List;

@Service
public class WebSocketBroadcastService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcastService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final UserFamilyMembershipRepository userFamilyMembershipRepository;
    private final UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository;

    public WebSocketBroadcastService(
            SimpMessagingTemplate messagingTemplate,
            UserFamilyMembershipRepository userFamilyMembershipRepository,
            UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userFamilyMembershipRepository = userFamilyMembershipRepository;
        this.userFamilyMessageSettingsRepository = userFamilyMessageSettingsRepository;
    }

    public void broadcastDMMessage(Map<String, Object> messageData, Long recipientId) {
        try {
            // Add recipient_id to the message data
            messageData.put("recipient_id", recipientId);
            String destination = "/topic/dm/" + recipientId;
            
            logger.debug("Broadcasting DM message to destination: {}", destination);
            messagingTemplate.convertAndSend(destination, messageData);
            logger.debug("Successfully broadcast DM message to {}", destination);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast DM message to recipient {}: {}", recipientId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    /**
     * Broadcast family message to all family members using user-specific topics.
     * This is the new improved architecture that eliminates the need for clients
     * to subscribe to multiple family topics.
     */
    public void broadcastFamilyMessage(Map<String, Object> messageData, Long familyId) {
        try {
            // Add family_id to the message data
            messageData.put("family_id", familyId);
            
            logger.debug("Broadcasting family message to family {} members", familyId);
            
            // Get all family members
            List<Long> familyMemberIds = userFamilyMembershipRepository.findByFamilyId(familyId)
                .stream()
                .map(membership -> membership.getUserId())
                .toList();
            
            logger.debug("Found {} members in family {}", familyMemberIds.size(), familyId);
            
            // Broadcast to each family member's individual topic (excluding muted users)
            int broadcastCount = 0;
            for (Long userId : familyMemberIds) {
                // Check if this user has muted this family
                boolean userHasMutedFamily = userFamilyMessageSettingsRepository
                    .findByUserIdAndFamilyId(userId, familyId)
                    .map(settings -> !settings.getReceiveMessages())
                    .orElse(false); // Default to receiving messages if no setting exists
                
                if (userHasMutedFamily) {
                    logger.debug("Skipping muted user {} for family {}", userId, familyId);
                    continue;
                }
                
                try {
                    String userDestination = "/user/" + userId + "/family";
                    messagingTemplate.convertAndSend(userDestination, messageData);
                    broadcastCount++;
                    logger.debug("Broadcast family message to user {}", userId);
                } catch (Exception e) {
                    logger.error("Failed to broadcast to user {}: {}", userId, e.getMessage());
                    // Continue to other users even if one fails
                }
            }
            
            logger.debug("Successfully broadcast family message to {}/{} family members", 
                broadcastCount, familyMemberIds.size());
            
            // Also broadcast to the legacy family topic for backward compatibility
            // This can be removed once all clients are updated
            String legacyDestination = "/family/" + familyId;
            try {
                messagingTemplate.convertAndSend(legacyDestination, messageData);
                logger.debug("Also broadcast to legacy destination: {}", legacyDestination);
            } catch (Exception e) {
                logger.warn("Failed to broadcast to legacy destination {}: {}", legacyDestination, e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to broadcast family message to family {}: {}", familyId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use broadcastFamilyMessage instead which handles user-specific broadcasting
     */
    @Deprecated
    public void broadcastFamilyMessageLegacy(Map<String, Object> messageData, Long familyId) {
        try {
            // Add family_id to the message data
            messageData.put("family_id", familyId);
            String destination = "/family/" + familyId;
            
            logger.debug("Broadcasting family message to legacy destination: {}", destination);
            messagingTemplate.convertAndSend(destination, messageData);
            logger.debug("Successfully broadcast family message to {}", destination);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast family message to family {}: {}", familyId, e.getMessage(), e);
            // Don't rethrow - we don't want WebSocket errors to break the main flow
        }
    }

    // Add more broadcast methods as needed
}
