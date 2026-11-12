package com.proben.telemetry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao de componentes e infraestrutura de jackson.
 */
@Configuration
public class JacksonConfig {
    /** Creates the Jackson configuration. */
    public JacksonConfig() {}

    /** @return ObjectMapper with discovered modules and ISO date serialization */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                 .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
