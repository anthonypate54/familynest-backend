package com.familynest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    private String type;
    private String content;
    private Object data;
    
    @JsonProperty("timestamp")
    @JsonDeserialize(using = TimestampDeserializer.class)
    @JsonSerialize(using = TimestampSerializer.class)
    private Long timestamp;

    // Default constructor
    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor for simple messages
    public WebSocketMessage(String type, String content) {
        this();
        this.type = type;
        this.content = content;
    }

    // Constructor for data messages
    public WebSocketMessage(String type, Object data) {
        this();
        this.type = type;
        this.data = data;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
