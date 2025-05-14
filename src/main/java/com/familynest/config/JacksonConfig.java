package com.familynest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Prevent infinite recursion in bidirectional relationships
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // Include null values in JSON output
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        
        return mapper;
    }
} 