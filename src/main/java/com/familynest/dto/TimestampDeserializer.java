package com.familynest.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimestampDeserializer extends JsonDeserializer<Long> {
    
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        
        if (node.isNumber()) {
            // Handle numeric timestamps (epoch milliseconds)
            return node.asLong();
        } else if (node.isTextual()) {
            // Handle string timestamps (ISO format)
            String timestampStr = node.asText();
            try {
                // Parse ISO timestamp and convert to epoch milliseconds
                LocalDateTime dateTime = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                // If parsing fails, return current time
                return System.currentTimeMillis();
            }
        }
        
        // Default fallback
        return System.currentTimeMillis();
    }
} 