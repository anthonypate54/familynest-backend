package com.familynest.controller;

import com.familynest.dto.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketTestController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestController.class);

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public WebSocketMessage handlePing(WebSocketMessage message) {
        logger.info("Received ping message: {}", message.getContent());
        
        WebSocketMessage response = new WebSocketMessage("pong", "Server received your ping!");
        logger.info("Sending pong response");
        
        return response;
    }

    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public WebSocketMessage handleEcho(WebSocketMessage message) {
        logger.info("Received echo message: {}", message.getContent());
        
        WebSocketMessage response = new WebSocketMessage("echo", "Echo: " + message.getContent());
        logger.info("Sending echo response");
        
        return response;
    }
}
