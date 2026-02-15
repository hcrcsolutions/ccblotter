package com.fmr.ec3.oscc.receiver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;

/**
 * ObjectMapper config for standalone kafka-receiver mode only.
 * Not {@code @Configuration} — must be explicitly {@code @Import}ed.
 * Host applications provide their own ObjectMapper bean.
 */
public class RedisConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
