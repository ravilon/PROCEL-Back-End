package com.procel.api.config;

import com.procel.api.integration.cobalto.CobaltoRoomsSource;
import com.procel.api.integration.resource.ResourceRoomsSource;
import com.procel.api.service.rooms.RoomsSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoomsSourceConfig {

    @Bean
    public RoomsSource roomsSource(
            @Value("${procel.rooms.source:resource}") String source,
            ResourceRoomsSource resourceRoomsSource,
            CobaltoRoomsSource cobaltoRoomsSource
    ) {
        return switch (source.toLowerCase()) {
            case "resource" -> resourceRoomsSource;
            case "cobalto" -> cobaltoRoomsSource;
            default -> throw new IllegalArgumentException("Invalid procel.rooms.source: " + source +
                    " (expected: resource | cobalto)");
        };
    }
}