package com.proben.api.config;

import com.proben.api.integration.cobalto.CobaltoRoomsSource;
import com.proben.api.integration.resource.ResourceRoomsSource;
import com.proben.api.service.rooms.RoomsSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoomsSourceConfig {

    @Bean
    public RoomsSource roomsSource(
            @Value("${proben.rooms.source:resource}") String source,
            ResourceRoomsSource resourceRoomsSource,
            CobaltoRoomsSource cobaltoRoomsSource
    ) {
        return switch (source.toLowerCase()) {
            case "resource" -> resourceRoomsSource;
            case "cobalto" -> cobaltoRoomsSource;
            default -> throw new IllegalArgumentException("Invalid proben.rooms.source: " + source +
                    " (expected: resource | cobalto)");
        };
    }
}