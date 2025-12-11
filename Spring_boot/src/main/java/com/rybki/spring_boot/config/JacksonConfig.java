package com.rybki.spring_boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configuration for Jackson ObjectMapper.
 * Registers modules for proper serialization of Java 8 date/time types.
 */
@Configuration
public class JacksonConfig {

    /**
     * Create and configure ObjectMapper with support for Java 8 date/time types.
     *
     * @return configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
