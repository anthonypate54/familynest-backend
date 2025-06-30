package com.familynest.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebSocketBroadcastService {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastDMMessage(Map<String, Object> messageData, Long recipientId) {
        // Add recipient_id to the message data
        messageData.put("recipient_id", recipientId);
        messagingTemplate.convertAndSend("/topic/dm/" + recipientId, messageData);
    }

    // Add more broadcast methods as needed
}
