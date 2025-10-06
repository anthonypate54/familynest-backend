package com.familynest.dto;

import java.util.Map;

/**
 * DTO for broadcasting DM messages via WebSocket.
 * Uses simple field names as-is.
 */
public class DMMessagePayload {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private String mediaUrl;
    private String mediaType;
    private String mediaThumbnail;
    private String mediaFilename;
    private Long mediaSize;
    private Integer mediaDuration;
    private Boolean isRead;
    private Long createdAt;
    private Long updatedAt;
    private Long deliveredAt;
    private Long recipientId;

    // Default constructor
    public DMMessagePayload() {}

    // Constructor from database result
    public static DMMessagePayload fromDatabaseResult(Map<String, Object> messageData, Long recipientId) {
        DMMessagePayload payload = new DMMessagePayload();
        
        // Set basic message fields
        payload.setId(getLongValue(messageData, "id"));
        payload.setConversationId(getLongValue(messageData, "conversation_id"));
        payload.setSenderId(getLongValue(messageData, "sender_id"));
        payload.setContent(getStringValue(messageData, "content"));
        payload.setMediaUrl(getStringValue(messageData, "media_url"));
        payload.setMediaType(getStringValue(messageData, "media_type"));
        payload.setMediaThumbnail(getStringValue(messageData, "media_thumbnail"));
        payload.setMediaFilename(getStringValue(messageData, "media_filename"));
        payload.setMediaSize(getLongValue(messageData, "media_size"));
        payload.setMediaDuration(getIntegerValue(messageData, "media_duration"));
        payload.setIsRead(getBooleanValue(messageData, "is_read"));
        
        // Set timestamp fields (convert to epoch milliseconds if needed)
        payload.setCreatedAt(getTimestampValue(messageData, "created_at"));
        payload.setUpdatedAt(getTimestampValue(messageData, "updated_at"));
        payload.setDeliveredAt(getTimestampValue(messageData, "delivered_at"));
        
        // Set recipient ID
        payload.setRecipientId(recipientId);
        
        return payload;
    }

    // Helper method to safely get Long values
    private static Long getLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    // Helper method to safely get Integer values
    private static Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    // Helper method to safely get String values
    private static String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    // Helper method to safely get Boolean values
    private static Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    // Helper method to convert timestamp to epoch milliseconds
    private static Long getTimestampValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    // Validation method
    public boolean isValid() {
        return id != null && conversationId != null && senderId != null && 
               content != null && recipientId != null;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getMediaThumbnail() { return mediaThumbnail; }
    public void setMediaThumbnail(String mediaThumbnail) { this.mediaThumbnail = mediaThumbnail; }

    public String getMediaFilename() { return mediaFilename; }
    public void setMediaFilename(String mediaFilename) { this.mediaFilename = mediaFilename; }

    public Long getMediaSize() { return mediaSize; }
    public void setMediaSize(Long mediaSize) { this.mediaSize = mediaSize; }

    public Integer getMediaDuration() { return mediaDuration; }
    public void setMediaDuration(Integer mediaDuration) { this.mediaDuration = mediaDuration; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public Long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Long deliveredAt) { this.deliveredAt = deliveredAt; }

    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

    @Override
    public String toString() {
        return "DMMessagePayload{" +
                "id=" + id +
                ", conversationId=" + conversationId +
                ", senderId=" + senderId +
                ", content='" + content + '\'' +
                ", mediaUrl='" + mediaUrl + '\'' +
                ", mediaType='" + mediaType + '\'' +
                ", mediaThumbnail='" + mediaThumbnail + '\'' +
                ", mediaFilename='" + mediaFilename + '\'' +
                ", mediaSize=" + mediaSize +
                ", mediaDuration=" + mediaDuration +
                ", isRead=" + isRead +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", deliveredAt=" + deliveredAt +
                ", recipientId=" + recipientId +
                '}';
    }
}
