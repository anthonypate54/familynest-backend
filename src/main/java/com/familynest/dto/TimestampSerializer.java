package com.familynest.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class TimestampSerializer extends JsonSerializer<Long> {
    
    @Override
    public void serialize(Long timestamp, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (timestamp != null) {
            generator.writeNumber(timestamp);
        } else {
            generator.writeNumber(System.currentTimeMillis());
        }
    }
} 
