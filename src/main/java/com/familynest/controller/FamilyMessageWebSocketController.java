package com.familynest.controller;

import com.familynest.dto.WebSocketMessage;
import com.familynest.model.Message;
import com.familynest.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class FamilyMessageWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(FamilyMessageWebSocketController.class);

    @Autowired
    private MessageService messageService;

    @MessageMapping("/family/join")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleFamilyJoin(@Payload WebSocketMessage message, 
                                           SimpMessageHeaderAccessor headerAccessor) {
        logger.info("User joining family chat: {}", message.getContent());
        
        // Extract family ID from the message data
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long userId = (Long) data.get("userId");
        
        // Add user to the family WebSocket session
        headerAccessor.getSessionAttributes().put("familyId", familyId);
        headerAccessor.getSessionAttributes().put("userId", userId);
        
        WebSocketMessage response = new WebSocketMessage("user_joined", data);
        logger.info("User {} joined family {} chat", userId, familyId);
        
        return response;
    }

    @MessageMapping("/family/leave")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleFamilyLeave(@Payload WebSocketMessage message) {
        logger.info("User leaving family chat: {}", message.getContent());
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long userId = (Long) data.get("userId");
        
        WebSocketMessage response = new WebSocketMessage("user_left", data);
        logger.info("User {} left family {} chat", userId, familyId);
        
        return response;
    }

    @MessageMapping("/family/typing")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleTyping(@Payload WebSocketMessage message) {
        logger.debug("User typing in family chat: {}", message.getContent());
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long userId = (Long) data.get("userId");
        Boolean isTyping = (Boolean) data.get("isTyping");
        
        WebSocketMessage response = new WebSocketMessage("typing", data);
        
        return response;
    }

    @MessageMapping("/family/message")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleNewMessage(@Payload WebSocketMessage message) {
        logger.info("New family message received: {}", message.getContent());
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long userId = (Long) data.get("userId");
        String content = (String) data.get("content");
        
        WebSocketMessage response = new WebSocketMessage("new_message", data);
        logger.info("Broadcasting new message to family {} from user {}", familyId, userId);
        
        return response;
    }

    @MessageMapping("/family/reaction")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleReaction(@Payload WebSocketMessage message) {
        logger.info("Family message reaction received: {}", message.getContent());
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long messageId = (Long) data.get("messageId");
        Long userId = (Long) data.get("userId");
        String reactionType = (String) data.get("reactionType");
        
        WebSocketMessage response = new WebSocketMessage("reaction", data);
        logger.info("Broadcasting reaction to family {} for message {} from user {}", 
            familyId, messageId, userId);
        
        return response;
    }

    @MessageMapping("/family/comment")
    @SendTo("/family/{familyId}")
    public WebSocketMessage handleComment(@Payload WebSocketMessage message) {
        logger.info("Family message comment received: {}", message.getContent());
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Long familyId = (Long) data.get("familyId");
        Long parentMessageId = (Long) data.get("parentMessageId");
        Long userId = (Long) data.get("userId");
        Long commentId = (Long) data.get("commentId");
        String content = (String) data.get("content");
        
        WebSocketMessage response = new WebSocketMessage("new_comment", data);
        logger.info("Broadcasting new comment to family {} for message {} from user {}", 
            familyId, parentMessageId, userId);
        
        return response;
    }
} 